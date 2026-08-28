package dev.fishbot.inventory;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Util;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Automatically selects the best food from the hotbar and holds the use
 * key until hunger regenerates above the configured threshold.
 */
public class AutoEater {

	private static final long EAT_TIMEOUT_MS = 8000;

	private final FishBotCore bot;

	private boolean eating = false;
	private int previousSlot = -1;
	private long eatStartedAt = 0;
	private int eatGraceTicks = 0;
	private boolean needRecast = false;

	public AutoEater(FishBotCore bot) {
		this.bot = bot;
	}

	public void tick(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		LocalPlayer player = mc.player;
		if (!config.enabled || !config.autoEat || player == null) {
			if (eating) {
				finish(mc);
			}
			return;
		}

		if (eating) {
			// Give the game a few ticks to register that the use key triggered
			// the eating animation (isUsingItem() lags a tick or two behind).
			if (eatGraceTicks > 0) {
				eatGraceTicks--;
			} else {
				// The use key stays down; finish when the item was consumed or on timeout.
				if (!player.isUsingItem() || Util.getMillis() - eatStartedAt > EAT_TIMEOUT_MS) {
					finish(mc);
					// Possibly continue eating on the next tick if still hungry.
				}
			}
			return;
		}

		if (bot.isScreenOpen() || bot.getPanic().isHalted()) {
			return;
		}

		int foodLevel = player.getFoodData().getFoodLevel();
		boolean hungry = foodLevel <= config.eatThreshold;
		boolean lowHealth = player.getHealth() <= 7.0F && player.getFoodData().needsFood();
		if (!hungry && !lowHealth) {
			return;
		}

		int slot = findBestFoodSlot(player, lowHealth);
		if (slot < 0) {
			return;
		}

		// If a bobber is cast, reel it in first so the fish can be eaten safely.
		if (bot.getFishing().hookExists()) {
			bot.getFishing().reelInForEat();
			needRecast = true;
		}

		previousSlot = player.getInventory().getSelectedSlot();
		player.getInventory().setSelectedSlot(slot);
		mc.options.keyUse.setDown(true);
		eating = true;
		eatStartedAt = Util.getMillis();
		eatGraceTicks = 5;
	}

	private int findBestFoodSlot(LocalPlayer player, boolean lowHealth) {
		int bestSlot = -1;
		int bestScore = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			// Enchanted golden apples are only worth it when near death.
			if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE && !lowHealth) {
				continue;
			}
			FoodProperties food = stack.getItem().components().get(DataComponents.FOOD);
			if (food == null) {
				continue;
			}
			int score = food.nutrition();
			// Prefer fish (cooked/raw cod & salmon) — they come straight from the
			// session and shouldn't be wasted as generic food.
			if (isFish(stack.getItem())) {
				score += 1000;
			}
			if (stack.getItem() == Items.GOLDEN_APPLE || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
				score += lowHealth ? 100 : -1; // prefer them only when hurt
			}
			if (score > bestScore) {
				bestScore = score;
				bestSlot = i;
			}
		}
		return bestSlot;
	}

	/** Fish variants that a fishing session naturally yields. */
	private static boolean isFish(net.minecraft.world.item.Item item) {
		return item == Items.COD || item == Items.SALMON
				|| item == Items.COOKED_COD || item == Items.COOKED_SALMON
				|| item == Items.TROPICAL_FISH || item == Items.PUFFERFISH;
	}

	private void finish(Minecraft mc) {
		mc.options.keyUse.setDown(false);
		if (mc.player != null && previousSlot >= 0) {
			mc.player.getInventory().setSelectedSlot(previousSlot);
		}
		boolean recast = needRecast;
		needRecast = false;
		previousSlot = -1;
		eating = false;
		eatGraceTicks = 0;
		// Resume fishing: switch back to the rod (previousSlot) and recast.
		if (recast) {
			bot.getFishing().castAfterEat(mc);
		}
	}

	/** Emergency stop (panic / world change). */
	public void forceStop() {
		Minecraft mc = Minecraft.getInstance();
		if (eating) {
			mc.options.keyUse.setDown(false);
		}
		eating = false;
		previousSlot = -1;
		eatGraceTicks = 0;
		needRecast = false;
	}

	public boolean isEating() {
		return eating;
	}
}
