package dev.fishbot.inventory;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Rod lifecycle management:
 * <ul>
 *   <li>cycling through hotbar rods as they wear out,</li>
 *   <li>break protection,</li>
 *   <li>Mending management — the mending rod is swapped into the off-hand
 *       while experience orbs are absorbed so it repairs itself.</li>
 * </ul>
 */
public class RodManager {

	private static final double ORB_RANGE_SQ = 3.5 * 3.5;
	private static final long SWAP_BACK_DELAY_MS = 3000;

	private final FishBotCore bot;

	private Holder<Enchantment> mendingHolder;
	private boolean mendingSwapped = false;
	private int mendingHotbarSlot = -1;
	private long lastOrbSeenAt = 0;
	private boolean noRodsAnnounced = false;

	public RodManager(FishBotCore bot) {
		this.bot = bot;
	}

	// ------------------------------------------------------------------
	// Per-tick (Mending off-hand management)
	// ------------------------------------------------------------------

	public void tick(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		LocalPlayer player = mc.player;
		if (!config.enabled || !config.mendingOffhand || player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		// Never touch the inventory while a container screen is open.
		if (mc.gui.screen() != null || player.containerMenu.containerId != 0) {
			return;
		}

		boolean orbNear = false;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof ExperienceOrb && entity.distanceToSqr(player) <= ORB_RANGE_SQ) {
				orbNear = true;
				break;
			}
		}
		long now = Util.getMillis();
		if (orbNear) {
			lastOrbSeenAt = now;
		}

		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();

		if (!mendingSwapped) {
			// Swap a damaged mending rod into the off-hand just before XP absorption.
			if (orbNear && ItemLists.isRod(main) && main.isDamageableItem() && main.getDamageValue() > 0
					&& hasMending(main) && off.isEmpty()) {
				int hotbarSlot = player.getInventory().getSelectedSlot();
				// Menu slot of hotbar slot i in the player inventory menu is 36 + i.
				mc.gameMode.handleContainerInput(0, 36 + hotbarSlot, 45 /* InventoryMenu.SHIELD_SLOT */,
						ContainerInput.SWAP, player);
				mendingSwapped = true;
				mendingHotbarSlot = hotbarSlot;
			}
		} else {
			// Swap back: orbs gone for a while, or the rod is fully repaired.
			boolean orbsGone = now - lastOrbSeenAt > SWAP_BACK_DELAY_MS;
			boolean repaired = !ItemLists.isRod(off) || !off.isDamageableItem() || off.getDamageValue() <= 0;
			if (orbsGone || repaired) {
				mc.gameMode.handleContainerInput(0, 36 + mendingHotbarSlot, 45 /* InventoryMenu.SHIELD_SLOT */,
						ContainerInput.SWAP, player);
				mendingSwapped = false;
				mendingHotbarSlot = -1;
			}
		}
	}

	public boolean hasMending(ItemStack stack) {
		Holder<Enchantment> holder = resolveMending();
		if (holder == null) {
			return false;
		}
		if (stack.getEnchantments().getLevel(holder) > 0) {
			return true;
		}
		// Enchanted books carry stored enchantments.
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		return stored.getLevel(holder) > 0;
	}

	private Holder<Enchantment> resolveMending() {
		if (mendingHolder == null) {
			try {
				Minecraft mc = Minecraft.getInstance();
				if (mc.level != null) {
					mendingHolder = mc.level.registryAccess()
							.lookupOrThrow(Registries.ENCHANTMENT)
							.getOrThrow(Enchantments.MENDING);
				}
			} catch (Throwable t) {
				return null;
			}
		}
		return mendingHolder;
	}

	/** Reset cached registry lookups when entering a new world. */
	public void onWorldChanged() {
		mendingHolder = null;
		mendingSwapped = false;
		mendingHotbarSlot = -1;
	}

	public boolean isMendingSwapped() {
		return mendingSwapped;
	}

	// ------------------------------------------------------------------
	// Rod cycling
	// ------------------------------------------------------------------

	public boolean shouldSwitch(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		if (!config.multiRod) {
			return false;
		}
		ItemStack rod = mc.player.getMainHandItem();
		if (!ItemLists.isRod(rod) || !rod.isDamageableItem()) {
			return false;
		}
		int remaining = rod.getMaxDamage() - rod.getDamageValue();
		return remaining <= Math.max(config.rodDurabilityThreshold, 1) && hasSpareRod(mc);
	}

	/** Is there another usable rod in the hotbar? */
	public boolean hasSpareRod(Minecraft mc) {
		return findUsableRodSlot(mc, -1) >= 0;
	}

	/**
	 * Find a hotbar slot with a usable rod.
	 *
	 * @param excludeSlot slot to skip (-1 for none)
	 * @return hotbar index or -1
	 */
	private int findUsableRodSlot(Minecraft mc, int excludeSlot) {
		FishBotConfig config = bot.getConfig();
		Inventory inv = mc.player.getInventory();
		int bestSlot = -1;
		int bestRemaining = -1;
		for (int i = 0; i < 9; i++) {
			if (i == excludeSlot) {
				continue;
			}
			ItemStack stack = inv.getItem(i);
			if (!ItemLists.isRod(stack)) {
				continue;
			}
			int remaining = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
			if (config.breakProtection && stack.isDamageableItem() && remaining <= 1) {
				continue; // would break on next use
			}
			if (remaining > bestRemaining) {
				bestRemaining = remaining;
				bestSlot = i;
			}
		}
		return bestSlot;
	}

	/** Switch the selected hotbar slot to the best spare rod. */
	public void switchToNextRod(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		int current = mc.player.getInventory().getSelectedSlot();
		int slot = findUsableRodSlot(mc, current);
		if (slot >= 0 && slot != current) {
			mc.player.getInventory().setSelectedSlot(slot);
			mc.player.sendSystemMessage(Component.translatable("fishbot.chat.rod_switched"));
			bot.getFishing().setNoRodsLeft(false);
			noRodsAnnounced = false;
		} else if (slot < 0) {
			announceNoRods(mc);
		}
	}

	/** Select any usable rod when nothing rod-like is held. */
	public void selectInitialRod(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		ItemStack selected = mc.player.getInventory().getSelectedItem();
		if (ItemLists.isRod(selected)) {
			return;
		}
		int slot = findUsableRodSlot(mc, -1);
		if (slot >= 0) {
			mc.player.getInventory().setSelectedSlot(slot);
			noRodsAnnounced = false;
			bot.getFishing().setNoRodsLeft(false);
		} else {
			announceNoRods(mc);
		}
	}

	private void announceNoRods(Minecraft mc) {
		if (noRodsAnnounced) {
			return;
		}
		noRodsAnnounced = true;
		bot.getFishing().setNoRodsLeft(true);
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.translatable("fishbot.chat.no_rods"));
		}
		bot.getDiscord().notify(Component.translatable("fishbot.chat.no_rods").getString(), 0xE67E22);
	}
}
