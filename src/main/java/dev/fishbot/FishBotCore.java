package dev.fishbot;

import dev.fishbot.config.ConfigManager;
import dev.fishbot.config.FishBotConfig;
import dev.fishbot.fishing.ActionScheduler;
import dev.fishbot.fishing.FishingCore;
import dev.fishbot.gui.HudOverlay;
import dev.fishbot.inventory.AutoEater;
import dev.fishbot.inventory.ChestManager;
import dev.fishbot.inventory.RodManager;
import dev.fishbot.notify.DiscordNotifier;
import dev.fishbot.safety.AntiAfk;
import dev.fishbot.safety.PanicSystem;
import dev.fishbot.stats.SessionStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Central orchestrator wiring all FishBot subsystems together.
 * Created once on client startup; ticked from the client tick event.
 */
public class FishBotCore {

	private final ConfigManager configManager;
	private final ActionScheduler scheduler = new ActionScheduler();
	private final SessionStats stats = new SessionStats();
	private final DiscordNotifier discord;
	private final FishingCore fishing;
	private final RodManager rodManager;
	private final ChestManager chestManager;
	private final AutoEater eater;
	private final PanicSystem panic;
	private final AntiAfk antiAfk;
	private final HudOverlay hud;

	private long lastTickMillis;

	public FishBotCore() {
		this.configManager = new ConfigManager();
		this.discord = new DiscordNotifier(this);
		this.fishing = new FishingCore(this);
		this.rodManager = new RodManager(this);
		this.chestManager = new ChestManager(this);
		this.eater = new AutoEater(this);
		this.panic = new PanicSystem(this);
		this.antiAfk = new AntiAfk(this);
		this.hud = new HudOverlay(this);
		this.hud.register();
		this.lastTickMillis = Util.getMillis();
	}

	// ------------------------------------------------------------------
	// Main tick (client thread)
	// ------------------------------------------------------------------

	public void tick(Minecraft mc) {
		lastTickMillis = Util.getMillis();
		if (mc.level == null || mc.player == null) {
			resetWorldState();
			return;
		}

		scheduler.tick();
		stats.tick(mc);
		panic.tick(mc);

		if (!panic.isHalted()) {
			eater.tick(mc);
			chestManager.tick(mc);
			fishing.tick(mc);
			if (!eater.isEating() && !chestManager.isActive()) {
				rodManager.tick(mc);
			}
		}

		antiAfk.tick(mc);
	}

	/** Called whenever the player leaves a world (state cleanup). */
	private void resetWorldState() {
		panic.reset();
		eater.forceStop();
		chestManager.abort();
		fishing.onWorldChanged();
		rodManager.onWorldChanged();
		stats.reset();
	}

	// ------------------------------------------------------------------
	// Pause coordination
	// ------------------------------------------------------------------

	/** Fishing is paused while eating / depositing / in manual GUIs / panicking. */
	public boolean isFishingPaused() {
		FishBotConfig config = getConfig();
		if (eater.isEating()) {
			return true;
		}
		if (chestManager.isActive()) {
			return true;
		}
		if (panic.isHalted()) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (config.pauseInGui && mc.gui.screen() instanceof AbstractContainerScreen<?>) {
			return true;
		}
		return false;
	}

	public boolean isScreenOpen() {
		return Minecraft.getInstance().gui.screen() != null;
	}

	/** Toggle the master switch (key binding). */
	public void toggleEnabled(Minecraft mc) {
		FishBotConfig config = getConfig();
		config.enabled = !config.enabled;
		configManager.save();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.translatable(
					config.enabled ? "fishbot.chat.enabled" : "fishbot.chat.disabled"));
		}
	}

	public void saveConfig() {
		configManager.save();
	}

	// ------------------------------------------------------------------
	// Accessors
	// ------------------------------------------------------------------

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public FishBotConfig getConfig() {
		return configManager.getConfig();
	}

	public ActionScheduler getScheduler() {
		return scheduler;
	}

	public SessionStats getStats() {
		return stats;
	}

	public DiscordNotifier getDiscord() {
		return discord;
	}

	public FishingCore getFishing() {
		return fishing;
	}

	public RodManager getRodManager() {
		return rodManager;
	}

	public ChestManager getChestManager() {
		return chestManager;
	}

	public AutoEater getEater() {
		return eater;
	}

	public PanicSystem getPanic() {
		return panic;
	}

	public AntiAfk getAntiAfk() {
		return antiAfk;
	}

	public HudOverlay getHud() {
		return hud;
	}

	public long lastTickMillis() {
		return lastTickMillis;
	}
}
