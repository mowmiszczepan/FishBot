package dev.fishbot.inventory;

import java.util.ArrayDeque;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * When the inventory fills up, finds a nearby chest / barrel / shulker box,
 * rotates towards it, opens it and stores loot according to the configured
 * keep/treasure/trash lists. Trash can be thrown away instead of stored.
 *
 * Implemented as a small state machine driven by the client tick.
 */
public class ChestManager {

	private static final double MAX_REACH = 4.0;
	private static final long OPEN_TIMEOUT_MS = 4000;
	private static final long CLOSE_COOLDOWN_MS = 10_000;
	private static final long FAIL_COOLDOWN_MS = 30_000;
	private static final long NO_CHEST_NOTICE_INTERVAL_MS = 120_000;

	private enum State {
		IDLE, SEARCH, APPROACH, OPEN, WAIT_SCREEN, TRANSFER, CLOSE, COOLDOWN
	}

	private record SlotAction(int menuSlot, boolean throwAway) {
	}

	private final FishBotCore bot;

	private State state = State.IDLE;
	private BlockPos target;
	private long stateDeadline;
	private long cooldownUntil;
	private long lastNoChestNoticeAt;
	private final ArrayDeque<SlotAction> transferQueue = new ArrayDeque<>();
	private int menuId = -1;
	private int movedItems;
	private boolean pendingManualRequest;
	private int transferTick;
	/** Positions already found to be completely full; skipped by the next search. */
	private final java.util.Set<Long> filledChests = new java.util.HashSet<>();

	public ChestManager(FishBotCore bot) {
		this.bot = bot;
	}

	/** Manual trigger from the GUI. */
	public void requestNow() {
		pendingManualRequest = true;
	}

	public boolean isActive() {
		return state != State.IDLE && state != State.COOLDOWN;
	}

	/** Anti-AFK must not fight our rotation while we aim at a chest. */
	public boolean isControllingRotation() {
		return state == State.APPROACH || state == State.OPEN;
	}

	/** Abort everything (panic, world change...). */
	public void abort() {
		state = State.IDLE;
		target = null;
		transferQueue.clear();
		menuId = -1;
		filledChests.clear();
		cooldownUntil = Math.max(cooldownUntil, now() + 2000);
	}

	// ------------------------------------------------------------------

	public void tick(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null) {
			abort();
			return;
		}
		// Safety: any panic aborts depositing immediately.
		if (bot.getPanic().isHalted()) {
			abort();
			return;
		}

		switch (state) {
			case IDLE -> {
				boolean full = inventoryUsedSlots(player) >= config.depositTriggerSlots;
				if ((pendingManualRequest || (config.enabled && config.autoDeposit && full)) && now() >= cooldownUntil) {
					pendingManualRequest = false;
					state = State.SEARCH;
				} else if (pendingManualRequest && now() < cooldownUntil) {
					pendingManualRequest = false; // swallowed by cooldown
				}
			}
			case SEARCH -> search(mc, player, config);
			case APPROACH -> {
				faceBlock(player, target);
				if (now() >= stateDeadline) {
					openContainer(mc, player);
				}
			}
			case OPEN -> {
				// useItemOn was sent; waiting is handled in WAIT_SCREEN.
				state = State.WAIT_SCREEN;
				stateDeadline = now() + OPEN_TIMEOUT_MS;
			}
			case WAIT_SCREEN -> waitScreen(mc);
			case TRANSFER -> transfer(mc);
			case CLOSE -> close(mc);
			case COOLDOWN -> {
				if (now() >= cooldownUntil) {
					state = State.IDLE;
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Steps
	// ------------------------------------------------------------------

	private void search(Minecraft mc, LocalPlayer player, FishBotConfig config) {
		int radius = Mth.clamp(config.depositRadius, 1, 6);
		BlockPos origin = player.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					// Skip containers already confirmed to be completely full.
					if (filledChests.contains(pos.asLong())) {
						continue;
					}
					Block block = mc.level.getBlockState(pos).getBlock();
					if (!(block instanceof AbstractChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock)) {
						continue;
					}
					Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
					double dist = center.distanceTo(player.getEyePosition());
					if (dist <= MAX_REACH && dist < bestDist) {
						bestDist = dist;
						best = pos;
					}
				}
			}
		}

		if (best == null) {
			if (now() - lastNoChestNoticeAt > NO_CHEST_NOTICE_INTERVAL_MS) {
				lastNoChestNoticeAt = now();
				player.sendSystemMessage(Component.translatable("fishbot.chat.no_chest"));
			}
			state = State.COOLDOWN;
			cooldownUntil = now() + FAIL_COOLDOWN_MS;
			return;
		}

		target = best;
		faceBlock(player, target);
		state = State.APPROACH;
		stateDeadline = now() + 150; // a few ticks of "looking at the chest"
	}

	private void openContainer(Minecraft mc, LocalPlayer player) {
		if (mc.gameMode == null || target == null) {
			abort();
			return;
		}
		Vec3 center = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		Direction side = Direction.fromYRot(player.getYRot()).getOpposite();
		BlockHitResult hit = new BlockHitResult(center, side, target, false);
		mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		state = State.OPEN;
	}

	private void waitScreen(Minecraft mc) {
		Screen screen = mc.gui.screen();
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			AbstractContainerMenu menu = containerScreen.getMenu();
			menuId = menu.containerId;
			buildTransferPlan(menu);
			// If there is something to deposit but the container has no free slot,
			// mark it as full, close it and let the next search pick another chest.
			if (!transferQueue.isEmpty() && !hasChestSpace(menu)) {
				if (target != null) {
					filledChests.add(target.asLong());
				}
				closeAndContinue(mc, menuId);
				return;
			}
			movedItems = 0;
			transferTick = 0;
			state = State.TRANSFER;
			return;
		}
		if (now() >= stateDeadline) {
			// Chest probably locked/protected by the server.
			state = State.COOLDOWN;
			cooldownUntil = now() + FAIL_COOLDOWN_MS;
			target = null;
		}
	}

	/** Close the current chest screen and immediately look for the next chest. */
	private void closeAndContinue(Minecraft mc, int containerId) {
		if (mc.player != null && mc.player.connection != null && containerId >= 0) {
			mc.player.connection.send(new ServerboundContainerClosePacket(containerId));
		}
		mc.gui.setScreen(null);
		target = null;
		menuId = -1;
		transferQueue.clear();
		// Short pause so the server registers the close before we reopen another.
		state = State.COOLDOWN;
		cooldownUntil = now() + 600;
	}

	/** Whether the opened container has at least one empty chest-side slot. */
	private static boolean hasChestSpace(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			// Chest-side slots use their own container, not the player Inventory.
			if (!(slot.container instanceof Inventory) && slot.getItem().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private void buildTransferPlan(AbstractContainerMenu menu) {
		FishBotConfig config = bot.getConfig();
		transferQueue.clear();

		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			// Player-side slots only (the chest's own slots use its own container).
			if (!(slot.container instanceof Inventory)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			// Never store fishing rods — the bot needs them to keep working.
			if (ItemLists.isRod(stack)) {
				continue;
			}
			if (ItemLists.matches(config.keepItems, stack)) {
				continue;
			}
			// Keep food around while auto-eat is enabled.
			if (config.autoEat && stack.getItem().components().get(
					net.minecraft.core.component.DataComponents.FOOD) != null) {
				continue;
			}

			boolean isTrash = ItemLists.matches(config.trashItems, stack);
			if (config.depositMode == FishBotConfig.DepositMode.KEEP_LIST) {
				if (isTrash && config.discardTrash) {
					transferQueue.add(new SlotAction(slot.index, true));
				} else {
					transferQueue.add(new SlotAction(slot.index, false));
				}
			} else { // TREASURES
				if (ItemLists.matches(config.treasureItems, stack)) {
					transferQueue.add(new SlotAction(slot.index, false));
				} else if (isTrash && config.discardTrash) {
					transferQueue.add(new SlotAction(slot.index, true));
				}
			}
		}
	}

	private void transfer(Minecraft mc) {
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen)) {
			// Screen closed under us (server closed it).
			abort();
			return;
		}
		AbstractContainerMenu menu = containerScreen.getMenu();
		if (menu.containerId != menuId || mc.player == null || mc.gameMode == null) {
			abort();
			return;
		}

		// Move roughly one item every other tick — looks human.
		transferTick++;
		if (transferTick % 2 == 0 && !transferQueue.isEmpty()) {
			SlotAction action = transferQueue.poll();
			if (action.menuSlot() >= 0 && action.menuSlot() < menu.slots.size()) {
				Slot slot = menu.slots.get(action.menuSlot());
				ItemStack stack = slot.getItem();
				if (!stack.isEmpty() && !ItemLists.isRod(stack)) {
					if (action.throwAway()) {
						mc.gameMode.handleContainerInput(menuId, action.menuSlot(), 1, ContainerInput.THROW, mc.player);
					} else {
						mc.gameMode.handleContainerInput(menuId, action.menuSlot(), 0, ContainerInput.QUICK_MOVE, mc.player);
					}
					movedItems++;
				}
			}
		}

		if (transferQueue.isEmpty()) {
			state = State.CLOSE;
		}
	}

	private void close(Minecraft mc) {
		if (mc.player != null && mc.player.connection != null && menuId >= 0) {
			mc.player.connection.send(new ServerboundContainerClosePacket(menuId));
		}
		mc.gui.setScreen(null);

		bot.getStats().onDeposit();
		FishBotConfig config = bot.getConfig();
		if (config.discordEnabled && config.discordOnStorage) {
			bot.getDiscord().notify(
					Component.translatable("fishbot.discord.deposit", movedItems).getString(), 0x2ECC71);
		}
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.translatable("fishbot.chat.deposit_done", movedItems));
		}

		state = State.COOLDOWN;
		cooldownUntil = now() + CLOSE_COOLDOWN_MS;
		target = null;
		menuId = -1;
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static int inventoryUsedSlots(LocalPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private static void faceBlock(LocalPlayer player, BlockPos pos) {
		Vec3 eye = player.getEyePosition();
		double dx = pos.getX() + 0.5 - eye.x;
		double dy = pos.getY() + 0.5 - eye.y;
		double dz = pos.getZ() + 0.5 - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
		float pitch = (float) (-(Mth.atan2(dy, horizontal) * 180.0F / (float) Math.PI));
		player.setYRot(yaw);
		player.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
	}

	private static long now() {
		return net.minecraft.util.Util.getMillis();
	}

	public State currentState() {
		return state;
	}
}
