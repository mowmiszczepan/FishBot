package dev.fishbot.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.ConfigManager;
import dev.fishbot.config.FishBotConfig;
import dev.fishbot.config.Presets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FishBot configuration GUI: tabbed screens with toggles, steppers,
 * lists and one-click presets.
 */
public class FishBotScreen extends Screen {

	private static final int ROW_HEIGHT = 24;
	private static final int BUTTON_WIDTH = 220;
	private static final int BUTTON_HEIGHT = 20;

	private enum Tab {
		FISHING, INVENTORY, SAFETY, INTERFACE, PRESETS
	}

	private final Screen parent;
	private final FishBotCore bot;
	private Tab tab = Tab.FISHING;

	public FishBotScreen(Component title, Screen parent, FishBotCore bot) {
		super(title);
		this.parent = parent;
		this.bot = bot;
	}

	@Override
	protected void init() {
		super.init();
		rebuild();
	}

	private void rebuild() {
		clearWidgets();

		// Tab buttons.
		Tab[] tabs = Tab.values();
		int tabWidth = 92;
		int totalWidth = tabs.length * (tabWidth + 4);
		int x = (this.width - totalWidth) / 2;
		for (Tab t : tabs) {
			final Tab ft = t;
			addRenderableWidget(Button.builder(Component.translatable("fishbot.tab." + t.name().toLowerCase()), btn -> {
				tab = ft;
				rebuild();
			}).bounds(x, 16, tabWidth, BUTTON_HEIGHT).build());
			x += tabWidth + 4;
		}

		switch (tab) {
			case FISHING -> buildFishingTab();
			case INVENTORY -> buildInventoryTab();
			case SAFETY -> buildSafetyTab();
			case INTERFACE -> buildInterfaceTab();
			case PRESETS -> buildPresetsTab();
		}

		// Done button.
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), btn -> onClose())
				.bounds(this.width / 2 - 100, this.height - 27, 200, BUTTON_HEIGHT).build());
	}

	// ------------------------------------------------------------------
	// Tabs
	// ------------------------------------------------------------------

	private void buildFishingTab() {
		FishBotConfig c = bot.getConfig();
		int x1 = leftCol();
		int x2 = rightCol();
		int y1 = top();
		int y2 = top();

		addToggle(x1, y1, "fishbot.opt.enabled", () -> c.enabled, v -> c.enabled = v);
		y1 += ROW_HEIGHT;
		addCycle(x1, y1, "fishbot.opt.detection", () -> c.detectionMode, v -> c.detectionMode = v,
				FishBotConfig.DetectionMode.values(), m -> Component.translatable("fishbot.detection." + m.name().toLowerCase()));
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.open_water", () -> c.openWaterDetection, v -> c.openWaterDetection = v);
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.persistent", () -> c.persistentMode, v -> c.persistentMode = v);
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.arm_swing", () -> c.armSwing, v -> c.armSwing = v);
		y1 += ROW_HEIGHT;

		addStepper(x2, y2, "fishbot.opt.reel_min", () -> c.reelInDelayMinMs, v -> {
			c.reelInDelayMinMs = v;
			if (c.reelInDelayMaxMs < v) {
				c.reelInDelayMaxMs = v;
			}
		}, 25, 0, 2000, "fishbot.unit.ms");
		y2 += ROW_HEIGHT;
		addStepper(x2, y2, "fishbot.opt.reel_max", () -> c.reelInDelayMaxMs, v -> {
			c.reelInDelayMaxMs = v;
			if (c.reelInDelayMinMs > v) {
				c.reelInDelayMinMs = v;
			}
		}, 25, 0, 3000, "fishbot.unit.ms");
		y2 += ROW_HEIGHT;
		addStepper(x2, y2, "fishbot.opt.recast", () -> (int) c.recastDelayMs, v -> c.recastDelayMs = v,
				100, 200, 10000, "fishbot.unit.ms");
		y2 += ROW_HEIGHT;
		addStepper(x2, y2, "fishbot.opt.jitter", () -> c.recastJitterPercent, v -> c.recastJitterPercent = v,
				5, 0, 100, "fishbot.unit.percent");
	}

	private void buildInventoryTab() {
		FishBotConfig c = bot.getConfig();
		int x1 = leftCol();
		int x2 = rightCol();
		int y1 = top();
		int y2 = top();

		addToggle(x1, y1, "fishbot.opt.multirod", () -> c.multiRod, v -> c.multiRod = v);
		y1 += ROW_HEIGHT;
		addStepper(x1, y1, "fishbot.opt.rod_threshold", () -> c.rodDurabilityThreshold,
				v -> c.rodDurabilityThreshold = v, 1, 2, 32, "fishbot.unit.durability");
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.break_protection", () -> c.breakProtection, v -> c.breakProtection = v);
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.mending", () -> c.mendingOffhand, v -> c.mendingOffhand = v);
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.auto_deposit", () -> c.autoDeposit, v -> c.autoDeposit = v);
		y1 += ROW_HEIGHT;
		addStepper(x1, y1, "fishbot.opt.deposit_radius", () -> c.depositRadius, v -> c.depositRadius = v,
				1, 1, 6, "fishbot.unit.blocks");
		y1 += ROW_HEIGHT;
		addStepper(x1, y1, "fishbot.opt.deposit_trigger", () -> c.depositTriggerSlots, v -> c.depositTriggerSlots = v,
				1, 5, 36, "fishbot.unit.slots");
		y1 += ROW_HEIGHT;

		addCycle(x2, y2, "fishbot.opt.deposit_mode", () -> c.depositMode, v -> c.depositMode = v,
				FishBotConfig.DepositMode.values(), m -> Component.translatable("fishbot.deposit." + m.name().toLowerCase()));
		y2 += ROW_HEIGHT;
		addToggle(x2, y2, "fishbot.opt.discard_trash", () -> c.discardTrash, v -> c.discardTrash = v);
		y2 += ROW_HEIGHT;
		addToggle(x2, y2, "fishbot.opt.auto_eat", () -> c.autoEat, v -> c.autoEat = v);
		y2 += ROW_HEIGHT;
		addStepper(x2, y2, "fishbot.opt.eat_threshold", () -> c.eatThreshold, v -> c.eatThreshold = v,
				1, 1, 19, "fishbot.unit.hunger");
		y2 += ROW_HEIGHT;
		addRenderableWidget(Button.builder(Component.translatable("fishbot.opt.deposit_now"), btn -> {
			bot.getChestManager().requestNow();
			onClose();
		}).bounds(x2, y2, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("fishbot.opt.deposit_now.tip"))).build());
		y2 += ROW_HEIGHT + 10;

		// Item lists (comma separated ids) under both columns.
		y1 += 8;
		int afterKeep = addListEditor(x1, y1, "fishbot.opt.keep_items", c.keepItems, v -> c.keepItems = v);
		addListEditor(x1, afterKeep + 2, "fishbot.opt.trash_items", c.trashItems, v -> c.trashItems = v);
		addListEditor(x2, y2, "fishbot.opt.treasure_items", c.treasureItems, v -> c.treasureItems = v);
	}

	private void buildSafetyTab() {
		FishBotConfig c = bot.getConfig();
		int x1 = leftCol();
		int x2 = rightCol();
		int y1 = top();
		int y2 = top();

		addToggle(x1, y1, "fishbot.opt.panic", () -> c.panicEnabled, v -> c.panicEnabled = v);
		y1 += ROW_HEIGHT;
		addStepper(x1, y1, "fishbot.opt.panic_health", () -> c.panicHealthPercent, v -> c.panicHealthPercent = v,
				5, 5, 90, "fishbot.unit.percent");
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.panic_damage", () -> c.panicOnDamage, v -> c.panicOnDamage = v);
		y1 += ROW_HEIGHT;
		addCycle(x1, y1, "fishbot.opt.panic_action", () -> c.panicAction, v -> c.panicAction = v,
				FishBotConfig.PanicAction.values(), a -> Component.translatable("fishbot.panic." + a.name().toLowerCase()));
		y1 += ROW_HEIGHT;

		addToggle(x2, y2, "fishbot.opt.antiafk", () -> c.antiAfk, v -> c.antiAfk = v);
		y2 += ROW_HEIGHT;
		addStepper(x2, y2, "fishbot.opt.antiafk_min", () -> c.antiAfkMinSec, v -> {
			c.antiAfkMinSec = v;
			if (c.antiAfkMaxSec < v) {
				c.antiAfkMaxSec = v;
			}
		}, 5, 5, 600, "fishbot.unit.sec");
		y2 += ROW_HEIGHT;
		addStepper(x2, y2, "fishbot.opt.antiafk_max", () -> c.antiAfkMaxSec, v -> {
			c.antiAfkMaxSec = v;
			if (c.antiAfkMinSec > v) {
				c.antiAfkMinSec = v;
			}
		}, 5, 5, 900, "fishbot.unit.sec");
	}

	private void buildInterfaceTab() {
		FishBotConfig c = bot.getConfig();
		int x1 = leftCol();
		int x2 = rightCol();
		int y1 = top();
		int y2 = top();

		addToggle(x1, y1, "fishbot.opt.hud", () -> c.hudEnabled, v -> c.hudEnabled = v);
		y1 += ROW_HEIGHT;
		addStepper(x1, y1, "fishbot.opt.hud_x", () -> c.hudX, v -> c.hudX = v, 4, 0, 400, "fishbot.unit.px");
		y1 += ROW_HEIGHT;
		addStepper(x1, y1, "fishbot.opt.hud_y", () -> c.hudY, v -> c.hudY = v, 4, 0, 240, "fishbot.unit.px");
		y1 += ROW_HEIGHT;
		addToggle(x1, y1, "fishbot.opt.pause_in_gui", () -> c.pauseInGui, v -> c.pauseInGui = v);
		y1 += ROW_HEIGHT;

		addToggle(x2, y2, "fishbot.opt.discord", () -> c.discordEnabled, v -> c.discordEnabled = v);
		y2 += ROW_HEIGHT;
		addToggle(x2, y2, "fishbot.opt.discord_rare", () -> c.discordOnRare, v -> c.discordOnRare = v);
		y2 += ROW_HEIGHT;
		addToggle(x2, y2, "fishbot.opt.discord_storage", () -> c.discordOnStorage, v -> c.discordOnStorage = v);
		y2 += ROW_HEIGHT;
		addToggle(x2, y2, "fishbot.opt.discord_attack", () -> c.discordOnAttack, v -> c.discordOnAttack = v);
		y2 += ROW_HEIGHT;

		addRenderableWidget(new StringWidget(x2, y2 + 2, BUTTON_WIDTH, 10,
				Component.translatable("fishbot.opt.webhook"), this.font));
		y2 += 14;
		EditBox webhook = new EditBox(this.font, x2, y2, BUTTON_WIDTH, BUTTON_HEIGHT,
				Component.translatable("fishbot.opt.webhook"));
		webhook.setMaxLength(300);
		webhook.setValue(c.discordWebhookUrl == null ? "" : c.discordWebhookUrl);
		webhook.setResponder(v -> c.discordWebhookUrl = v.trim());
		addRenderableWidget(webhook);
		y2 += ROW_HEIGHT + 4;
		addRenderableWidget(Button.builder(Component.translatable("fishbot.opt.discord_test"), btn -> {
			if (dev.fishbot.notify.DiscordNotifier.isValidWebhook(c.discordWebhookUrl)) {
				boolean wasEnabled = c.discordEnabled;
				c.discordEnabled = true;
				bot.getDiscord().notify(Component.translatable("fishbot.discord.test").getString(), 0x3498DB);
				c.discordEnabled = wasEnabled;
			}
		}).bounds(x2, y2, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("fishbot.opt.discord_test.tip"))).build());
	}

	private void buildPresetsTab() {
		int x1 = leftCol();
		int x2 = rightCol();
		int y1 = top();
		int y2 = top();

		addPresetButton(x1, y1, "fishbot.preset.overnight", Presets.overnightAfk());
		y1 += ROW_HEIGHT;
		addPresetButton(x1, y1, "fishbot.preset.treasure", Presets.treasureHunter());
		y1 += ROW_HEIGHT;
		addPresetButton(x1, y1, "fishbot.preset.fast", Presets.fastFishing());
		y1 += ROW_HEIGHT;
		addPresetButton(x1, y1, "fishbot.preset.defaults", Presets.defaults());
		y1 += ROW_HEIGHT;

		addRenderableWidget(Button.builder(Component.translatable("fishbot.preset.save"), btn -> {
			String name = "custom_" + java.time.LocalDateTime.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			bot.getConfigManager().saveCustomPreset(name, ConfigManager.copy(bot.getConfig()));
			rebuild();
		}).bounds(x2, y2, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("fishbot.preset.save.tip"))).build());
		y2 += ROW_HEIGHT + 8;

		for (String name : bot.getConfigManager().listCustomPresets()) {
			if (y2 > this.height - 60) {
				break;
			}
			addRenderableWidget(Button.builder(Component.literal(name), btn -> applyPreset(
					bot.getConfigManager().loadCustomPreset(name)))
					.bounds(x2, y2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			y2 += ROW_HEIGHT;
		}
	}

	private void addPresetButton(int x, int y, String key, FishBotConfig preset) {
		addRenderableWidget(Button.builder(Component.translatable(key), btn -> applyPreset(preset))
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(key + ".tip"))).build());
	}

	private void applyPreset(FishBotConfig preset) {
		if (preset == null) {
			return;
		}
		bot.getConfigManager().setConfig(preset);
		rebuild();
	}

	// ------------------------------------------------------------------
	// Widget factories
	// ------------------------------------------------------------------

	private int leftCol() {
		return this.width / 2 - BUTTON_WIDTH - 12;
	}

	private int rightCol() {
		return this.width / 2 + 12;
	}

	private int top() {
		return 44;
	}

	private void addToggle(int x, int y, String key, Supplier<Boolean> get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(toggleText(key, get.get()), btn -> {
			set.accept(!get.get());
			btn.setMessage(toggleText(key, get.get()));
			bot.saveConfig();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(key + ".tip"))).build());
	}

	private Component toggleText(String key, boolean value) {
		return Component.translatable(key).append(": ")
				.append(Component.translatable(value ? "fishbot.on" : "fishbot.off")
						.withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private <T> void addCycle(int x, int y, String key, Supplier<T> get, Consumer<T> set,
			T[] values, Function<T, Component> name) {
		addRenderableWidget(Button.builder(cycleText(key, name.apply(get.get())), btn -> {
			List<T> list = Arrays.asList(values);
			int idx = Math.max(0, list.indexOf(get.get()));
			set.accept(values[(idx + 1) % values.length]);
			btn.setMessage(cycleText(key, name.apply(get.get())));
			bot.saveConfig();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(key + ".tip"))).build());
	}

	private Component cycleText(String key, Component value) {
		return Component.translatable(key).append(": ").append(value.withStyle(ChatFormatting.AQUA));
	}

	private void addStepper(int x, int y, String key, Supplier<Integer> get, Consumer<Integer> set,
			int step, int min, int max, String unitKey) {
		Button[] valueButton = new Button[1];

		valueButton[0] = Button.builder(stepperText(key, get.get(), unitKey), btn -> {
			// Clicking the value itself cycles up too.
			set.accept(clamp(get.get() + step, min, max));
			valueButton[0].setMessage(stepperText(key, get.get(), unitKey));
			bot.saveConfig();
		}).bounds(x + 24, y, BUTTON_WIDTH - 48, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable(key + ".tip"))).build();

		addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
			set.accept(clamp(get.get() - step, min, max));
			valueButton[0].setMessage(stepperText(key, get.get(), unitKey));
			bot.saveConfig();
		}).bounds(x, y, 20, BUTTON_HEIGHT).build());
		addRenderableWidget(valueButton[0]);
		addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
			set.accept(clamp(get.get() + step, min, max));
			valueButton[0].setMessage(stepperText(key, get.get(), unitKey));
			bot.saveConfig();
		}).bounds(x + BUTTON_WIDTH - 20, y, 20, BUTTON_HEIGHT).build());
	}

	private Component stepperText(String key, int value, String unitKey) {
		return Component.translatable(key).append(": ").append(Component.literal(value + " ")
				.append(Component.translatable(unitKey)).withStyle(ChatFormatting.AQUA));
	}

	private int addListEditor(int x, int y, String key, List<String> list, Consumer<List<String>> set) {
		addRenderableWidget(new StringWidget(x, y, BUTTON_WIDTH, 10, Component.translatable(key), this.font));
		EditBox box = new EditBox(this.font, x, y + 12, BUTTON_WIDTH + 60, BUTTON_HEIGHT, Component.translatable(key));
		box.setMaxLength(1000);
		box.setValue(String.join(", ", list));
		box.setResponder(text -> {
			List<String> parsed = new ArrayList<>();
			for (String part : text.split(",")) {
				String trimmed = part.trim();
				if (!trimmed.isEmpty()) {
					parsed.add(trimmed);
				}
			}
			set.accept(parsed);
			bot.saveConfig();
		});
		addRenderableWidget(box);
		return y + 12 + BUTTON_HEIGHT + 10;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------------
	// Screen plumbing
	// ------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		this.extractTransparentBackground(graphics);
		graphics.centeredText(this.font, this.title, this.width / 2, 4, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		bot.saveConfig();
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
