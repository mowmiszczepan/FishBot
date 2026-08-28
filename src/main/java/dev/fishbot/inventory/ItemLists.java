package dev.fishbot.inventory;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Helpers for matching ItemStacks against configured id lists.
 */
public final class ItemLists {

	private ItemLists() {
	}

	/** Registry id of the stack's item, e.g. {@code minecraft:cod}. */
	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	/** Normalize a configured entry (allow bare names without namespace). */
	public static String normalize(String entry) {
		String trimmed = entry.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		return trimmed.indexOf(':') >= 0 ? trimmed : "minecraft:" + trimmed;
	}

	public static boolean matches(List<String> configuredIds, ItemStack stack) {
		if (configuredIds == null || configuredIds.isEmpty() || stack.isEmpty()) {
			return false;
		}
		String id = itemId(stack);
		for (String entry : configuredIds) {
			if (normalize(entry).equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isRod(ItemStack stack) {
		Item item = stack.getItem();
		return item == Items.FISHING_ROD || item instanceof FishingRodItem;
	}
}
