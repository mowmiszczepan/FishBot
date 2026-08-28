package dev.fishbot.fishing;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import dev.fishbot.inventory.ItemLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The fishing state machine: casts, bite handling, humanized delays,
 * open-water validation, durability protection and persistent mode.
 */
public class FishingCore {

	private static final long PERSISTENT_CHECK_INTERVAL_MS = 10_000L;

	private final FishBotCore bot;
	private final FishMonitor monitor = new FishMonitor(this);
	private final LootScanner lootScanner;

	private boolean hookExists = false;
	private long hookRemovedAt = 0L;
	private long timeMillis = 0L;
	private boolean recastQueued = false;
	private boolean switchScheduled = false;
	private boolean noRodsLeft = false;
	private boolean haltedForPanic = false;
	private boolean holdRecast = false;
	private OpenWaterState lastOpenWaterState = OpenWaterState.UNKNOWN;
	private boolean openWaterMessageShown = false;

	public FishingCore(FishBotCore bot) {
		this.bot = bot;
		this.lootScanner = new LootScanner(bot);
		bot.getScheduler().scheduleRepeating(PERSISTENT_CHECK_INTERVAL_MS, this::checkPersistentMode);
	}

	// ------------------------------------------------------------------
	// Per-tick update
	// ------------------------------------------------------------------

	public void tick(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		timeMillis = Util.getMillis();

		if (!config.enabled || haltedForPanic || noRodsLeft) {
			return;
		}
		if (bot.isFishingPaused()) {
			return;
		}

		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}

		if (isHoldingFishingRod(player)) {
			if (player.fishing != null) {
				hookExists = true;
				if (config.detectionMode != FishBotConfig.DetectionMode.PACKET) {
					monitor.hookTick(player.fishing);
				}
				checkRodDurability(mc);
			} else {
				removeHook();
			}
		} else {
			removeHook();
			// Nothing in hand: try to pick a rod from the hotbar once.
			if (config.multiRod && !hookExists) {
				bot.getRodManager().selectInitialRod(mc);
			}
		}
	}

	// ------------------------------------------------------------------
	// Bite handling
	// ------------------------------------------------------------------

	/**
	 * Called by {@link FishMonitor} the moment a bite is detected.
	 * Schedules the humanized reel-in and the follow-up recast.
	 */
	public void onBiteDetected() {
		Minecraft mc = Minecraft.getInstance();
		FishBotConfig config = bot.getConfig();
		if (!config.enabled || recastQueued || bot.isFishingPaused()) {
			return;
		}
		if (mc.player == null) {
			return;
		}

		bot.getStats().onCatch();

		// Watch the drops to spot treasure loot.
		if (mc.player.fishing != null) {
			lootScanner.scanAfterCatch(mc.player.fishing.position());
		}

		// 1) Humanized reel-in delay.
		long reelDelay = ActionScheduler.randomDelay(config.reelInDelayMinMs, config.reelInDelayMaxMs);
		bot.getScheduler().schedule(reelDelay, this::useRod);

		// 2) Rod switch right after the catch if the rod is wearing out.
		if (config.multiRod && bot.getRodManager().shouldSwitch(mc)) {
			switchScheduled = true;
			bot.getScheduler().schedule(reelDelay + ActionScheduler.randomDelay(60, 140), () -> {
				switchScheduled = false;
				bot.getRodManager().switchToNextRod(mc);
			});
		}

		// 3) Recast after the catch.
		long recastDelay = ActionScheduler.jitter(config.recastDelayMs, config.recastJitterPercent);
		recastQueued = true;
		bot.getScheduler().schedule(reelDelay + recastDelay, () -> {
			recastQueued = false;
			tryRecast();
		});
	}

	private void tryRecast() {
		Minecraft mc = Minecraft.getInstance();
		if (hookExists || holdRecast) {
			return;
		}
		if (mc.player == null || !isHoldingFishingRod(mc.player)) {
			return;
		}
		if (!bot.getConfig().enabled || bot.isFishingPaused()) {
			return;
		}
		useRod();
		bot.getStats().onCast();
		scheduleOpenWaterCheck();
	}

	/** Queue a plain recast (used by persistent mode and the clear-lag hook). */
	public void queueRecast() {
		if (recastQueued || holdRecast) {
			return;
		}
		FishBotConfig config = bot.getConfig();
		recastQueued = true;
		long delay = ActionScheduler.randomDelay(config.reelInDelayMinMs, config.reelInDelayMaxMs)
				+ ActionScheduler.jitter(config.recastDelayMs, config.recastJitterPercent);
		bot.getScheduler().schedule(delay, () -> {
			recastQueued = false;
			tryRecast();
		});
	}

	// ------------------------------------------------------------------
	// Rod durability protection
	// ------------------------------------------------------------------

	private void checkRodDurability(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		if (switchScheduled || recastQueued) {
			return;
		}
		ItemStack rod = getHeldRod(mc.player);
		if (rod == null || rod.isEmpty() || !rod.isDamageableItem()) {
			return;
		}
		int remaining = rod.getMaxDamage() - rod.getDamageValue();

		// Emergency: one use away from breaking — reel in immediately.
		if (config.breakProtection && remaining <= 1) {
			switchScheduled = true;
			bot.getScheduler().schedule(ActionScheduler.randomDelay(50, 120), () -> {
				switchScheduled = false;
				useRod(); // reel in
				if (config.multiRod) {
					bot.getRodManager().switchToNextRod(mc);
				}
				queueRecast();
			});
			return;
		}

		// Normal threshold switch: finish gracefully.
		if (config.multiRod && config.breakProtection && remaining <= config.rodDurabilityThreshold
				&& bot.getRodManager().hasSpareRod(mc)) {
			switchScheduled = true;
			bot.getScheduler().schedule(ActionScheduler.randomDelay(150, 400), () -> {
				switchScheduled = false;
				useRod(); // reel in
				bot.getRodManager().switchToNextRod(mc);
				queueRecast();
			});
		}
	}

	// ------------------------------------------------------------------
	// Persistent mode
	// ------------------------------------------------------------------

	private void checkPersistentMode() {
		Minecraft mc = Minecraft.getInstance();
		FishBotConfig config = bot.getConfig();
		if (!config.enabled || !config.persistentMode || haltedForPanic || noRodsLeft) {
			return;
		}
		if (mc.player == null || mc.level == null || bot.isFishingPaused()) {
			return;
		}
		if (!isHoldingFishingRod(mc.player) || recastQueued || switchScheduled) {
			return;
		}

		if (hookExists) {
			if (isBobberInWater(mc)) {
				return;
			}
			// Hook landed somewhere useless: reel it and let the recast run.
			queueRecast();
			useRod();
			return;
		}

		useRod();
		bot.getStats().onCast();
		scheduleOpenWaterCheck();
	}

	private boolean isBobberInWater(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.player.fishing == null) {
			return false;
		}
		Block block = mc.level.getBlockState(mc.player.fishing.blockPosition()).getBlock();
		return block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN;
	}

	// ------------------------------------------------------------------
	// Open water validation (1.16+ treasure rules)
	// ------------------------------------------------------------------

	private void detectOpenWater(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		if (!config.openWaterDetection) {
			return;
		}
		if (mc.player == null || mc.player.fishing == null || mc.level == null) {
			return;
		}
		FishingHook hook = mc.player.fishing;
		boolean open = OpenWaterValidator.isOpenWater(mc.level, hook.blockPosition());

		// Always refresh the HUD indicator, but only spam the overlay message
		// on the very first cast of a session (not on every single cast).
		lastOpenWaterState = open ? OpenWaterState.SUCCESS : OpenWaterState.FAIL;
		if (!openWaterMessageShown) {
			openWaterMessageShown = true;
			mc.player.sendOverlayMessage(Component.translatable(
					open ? "fishbot.openwater.ok" : "fishbot.openwater.fail"));
		}
	}

	/**
	 * Run the open-water validation shortly after the bobber has landed in the
	 * water (rather than waiting until a bite), so the player sees the result
	 * right after casting.
	 */
	private void scheduleOpenWaterCheck() {
		bot.getScheduler().schedule(1200, () -> detectOpenWater(Minecraft.getInstance()));
	}

	/** Latest open-water result, for the HUD. */
	public OpenWaterState getOpenWaterState() {
		return hookExists ? lastOpenWaterState : OpenWaterState.UNKNOWN;
	}

	// ------------------------------------------------------------------
	// Rod usage
	// ------------------------------------------------------------------

	/** Right-click with the rod in the correct hand. */
	public void useRod() {
		Minecraft mc = Minecraft.getInstance();
		FishBotConfig config = bot.getConfig();
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		InteractionHand hand = getCorrectHand(mc.player);
		if (hand == null) {
			return;
		}
		if (config.armSwing) {
			mc.player.swing(hand);
		}
		InteractionResult result = mc.gameMode.useItem(mc.player, hand);
		if (result != null && result.consumesAction()) {
			mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
		}
	}

	// ------------------------------------------------------------------
	// Auto-eat coordination
	// ------------------------------------------------------------------

	/**
	 * Reel in a cast bobber so the player can eat safely, and hold off any
	 * automatic recast until {@link #castAfterEat} is called.
	 */
	public void reelInForEat() {
		if (hookExists) {
			useRod(); // reel in
		}
		recastQueued = false;
		holdRecast = true;
	}

	/** Select the fishing rod and immediately recast once eating has finished. */
	public void castAfterEat(Minecraft mc) {
		holdRecast = false;
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (!isHoldingFishingRod(mc.player)) {
			return;
		}
		useRod();
		bot.getStats().onCast();
		scheduleOpenWaterCheck();
	}

	private InteractionHand getCorrectHand(LocalPlayer player) {
		if (ItemLists.isRod(player.getMainHandItem())) {
			return InteractionHand.MAIN_HAND;
		}
		if (ItemLists.isRod(player.getOffhandItem())) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	public boolean isHoldingFishingRod(LocalPlayer player) {
		return getCorrectHand(player) != null;
	}

	private ItemStack getHeldRod(LocalPlayer player) {
		InteractionHand hand = getCorrectHand(player);
		return hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
	}

	// ------------------------------------------------------------------
	// Hook lifecycle
	// ------------------------------------------------------------------

	private void removeHook() {
		if (hookExists) {
			hookExists = false;
			hookRemovedAt = timeMillis;
			monitor.handleHookRemoved();
			lastOpenWaterState = OpenWaterState.UNKNOWN;
		}
	}

	public boolean hookExists() {
		return hookExists;
	}

	public long hookRemovedAt() {
		return hookRemovedAt;
	}

	public boolean isRecastQueued() {
		return recastQueued;
	}

	public long timeMillis() {
		return timeMillis;
	}

	// ------------------------------------------------------------------
	// External control
	// ------------------------------------------------------------------

	public void haltForPanic() {
		haltedForPanic = true;
		recastQueued = false;
		switchScheduled = false;
	}

	public void resumeAfterPanic() {
		haltedForPanic = false;
	}

	public boolean isHalted() {
		return haltedForPanic;
	}

	public void setNoRodsLeft(boolean value) {
		this.noRodsLeft = value;
	}

	public boolean isNoRodsLeft() {
		return noRodsLeft;
	}

	public void onWorldChanged() {
		hookExists = false;
		recastQueued = false;
		switchScheduled = false;
		noRodsLeft = false;
		haltedForPanic = false;
		holdRecast = false;
		lastOpenWaterState = OpenWaterState.UNKNOWN;
		openWaterMessageShown = false;
		monitor.handleHookRemoved();
	}

	public FishMonitor getMonitor() {
		return monitor;
	}

	public enum OpenWaterState {
		UNKNOWN,
		SUCCESS,
		FAIL
	}
}
