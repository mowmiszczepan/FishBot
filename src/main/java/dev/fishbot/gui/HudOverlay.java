package dev.fishbot.gui;

import java.util.ArrayList;
import java.util.List;

import dev.fishbot.FishBot;
import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import dev.fishbot.fishing.FishingCore;
import dev.fishbot.inventory.ItemLists;
import dev.fishbot.stats.SessionStats;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Live statistics overlay: session time, catches, rare drops, EXP/h and
 * remaining rod durability.
 */
public class HudOverlay {

	private static final int TEXT_COLOR = 0xFFDDDDDD;
	private static final int TITLE_COLOR = 0xFF55FFFF;
	private static final int GOOD_COLOR = 0xFF55FF55;
	private static final int WARN_COLOR = 0xFFFFAA00;
	private static final int BAD_COLOR = 0xFFFF5555;
	private static final int BACKGROUND_COLOR = 0x90101018;

	private final FishBotCore bot;

	public HudOverlay(FishBotCore bot) {
		this.bot = bot;
	}

	public void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(FishBot.MOD_ID, "stats_hud"), this::extract);
	}

	private void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		FishBotConfig config = bot.getConfig();
		if (!config.hudEnabled || mc.player == null || mc.level == null || mc.font == null) {
			return;
		}

		Font font = mc.font;
		SessionStats stats = bot.getStats();

		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		// Title + status
		lines.add("FishBot " + statusText());
		colors.add(TITLE_COLOR);

		// Session stats
		lines.add(Component.translatable("fishbot.hud.time",
				SessionStats.formatDuration(stats.sessionMillis())).getString());
		colors.add(TEXT_COLOR);

		lines.add(Component.translatable("fishbot.hud.fish",
				stats.getCatches(), Math.round(stats.catchesPerHour())).getString());
		colors.add(TEXT_COLOR);

		if (stats.getRareCatches() > 0 || config.discordEnabled) {
			lines.add(Component.translatable("fishbot.hud.rare", stats.getRareCatches()).getString());
			colors.add(WARN_COLOR);
		}

		if (stats.getXpGained() > 0) {
			lines.add(Component.translatable("fishbot.hud.xp",
					stats.getXpGained(), Math.round(stats.xpPerHour())).getString());
			colors.add(GOOD_COLOR);
		}

		// Rod durability (hotbar rods)
		addRodLines(mc, lines, colors);

		// Open water indicator while the hook is out
		FishingCore.OpenWaterState openWater = bot.getFishing().getOpenWaterState();
		if (openWater == FishingCore.OpenWaterState.SUCCESS) {
			lines.add(Component.translatable("fishbot.hud.openwater.ok").getString());
			colors.add(GOOD_COLOR);
		} else if (openWater == FishingCore.OpenWaterState.FAIL) {
			lines.add(Component.translatable("fishbot.hud.openwater.fail").getString());
			colors.add(BAD_COLOR);
		}

		// Measure and draw the panel.
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}
		int x = Math.max(0, Math.min(config.hudX, graphics.guiWidth() - width - 8));
		int y = Math.max(0, Math.min(config.hudY, graphics.guiHeight() - lines.size() * (font.lineHeight + 1) - 8));
		int height = lines.size() * (font.lineHeight + 1) + 5;

		graphics.fill(x, y, x + width + 8, y + height, BACKGROUND_COLOR);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), x + 4, y + 3 + i * (font.lineHeight + 1), colors.get(i), true);
		}
	}

	private void addRodLines(Minecraft mc, List<String> lines, List<Integer> colors) {
		Inventory inv = mc.player.getInventory();
		int shown = 0;
		for (int i = 0; i < 9 && shown < 4; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty() || !ItemLists.isRod(stack) || !stack.isDamageableItem()) {
				continue;
			}
			int max = stack.getMaxDamage();
			int remaining = max - stack.getDamageValue();
			int percent = max > 0 ? (remaining * 100 / max) : 0;
			int barLen = 8;
			int filled = Math.round(percent / 100.0F * barLen);
			StringBuilder bar = new StringBuilder("[");
			for (int b = 0; b < barLen; b++) {
				bar.append(b < filled ? '#' : '-');
			}
			bar.append("] ").append(remaining).append('/').append(max);
			lines.add(Component.translatable("fishbot.hud.rod", bar.toString()).getString());
			colors.add(percent > 40 ? GOOD_COLOR : (percent > 15 ? WARN_COLOR : BAD_COLOR));
			shown++;
		}
	}

	private String statusText() {
		FishBotConfig config = bot.getConfig();
		if (bot.getPanic().isHalted()) {
			return Component.translatable("fishbot.hud.status.panic").getString();
		}
		if (!config.enabled) {
			return Component.translatable("fishbot.hud.status.off").getString();
		}
		if (bot.getEater().isEating()) {
			return Component.translatable("fishbot.hud.status.eating").getString();
		}
		if (bot.getChestManager().isActive()) {
			return Component.translatable("fishbot.hud.status.deposit").getString();
		}
		if (bot.isFishingPaused()) {
			return Component.translatable("fishbot.hud.status.paused").getString();
		}
		return Component.translatable("fishbot.hud.status.fishing").getString();
	}
}
