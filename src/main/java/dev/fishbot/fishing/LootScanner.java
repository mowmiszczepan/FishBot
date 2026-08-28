package dev.fishbot.fishing;

import java.util.HashSet;
import java.util.Set;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import dev.fishbot.inventory.ItemLists;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * After a catch, scans for item drops near the bobber to detect treasure
 * loot (rare catches) for the HUD statistics and Discord alerts.
 *
 * <p>The catch itself is resolved server-side, so the dropped item entity
 * is the reliable client-visible signal of what was caught.
 */
public class LootScanner {

	private static final double SCAN_RADIUS_SQ = 4.0 * 4.0;
	private static final long[] SCAN_DELAYS_MS = {600, 1600, 2800};

	private final FishBotCore bot;
	private final Set<Integer> countedEntities = new HashSet<>();

	public LootScanner(FishBotCore bot) {
		this.bot = bot;
	}

	/** Schedule a few scans around the moment the catch lands. */
	public void scanAfterCatch(Vec3 hookPos) {
		countedEntities.clear();
		for (long delay : SCAN_DELAYS_MS) {
			bot.getScheduler().schedule(delay, () -> scan(hookPos));
		}
	}

	private void scan(Vec3 pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		FishBotConfig config = bot.getConfig();

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof ItemEntity itemEntity)) {
				continue;
			}
			if (entity.distanceToSqr(pos.x, pos.y, pos.z) > SCAN_RADIUS_SQ) {
				continue;
			}
			if (countedEntities.contains(entity.getId())) {
				continue;
			}
			ItemStack stack = itemEntity.getItem();
			if (stack.isEmpty() || !isRareCatch(stack)) {
				continue;
			}
			countedEntities.add(entity.getId());
			bot.getStats().onRareCatch();
			if (config.discordEnabled && config.discordOnRare) {
				bot.getDiscord().notifyRareCatch(stack.getHoverName().getString());
			}
		}
	}

	/**
	 * Fixed rare-drop rule: an enchanted book, an enchanted bow or an enchanted
	 * fishing rod. This is intentionally independent of any user-editable list,
	 * so it cannot be configured away or accidentally changed.
	 */
	private boolean isRareCatch(ItemStack stack) {
		Item item = stack.getItem();
		boolean isBook = item == Items.ENCHANTED_BOOK;
		boolean isBow = item == Items.BOW;
		boolean isRod = ItemLists.isRod(stack);
		if (!isBook && !isBow && !isRod) {
			return false;
		}
		// Enchanted books are always enchanted; bows and rods only count when
		// actually enchanted (a plain/caught item is not a treasure drop).
		return isBook || stack.isEnchanted();
	}
}
