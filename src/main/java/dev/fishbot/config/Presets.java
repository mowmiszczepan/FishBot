package dev.fishbot.config;

/**
 * Built-in one-click configuration profiles.
 */
public final class Presets {

	private Presets() {
	}

	/** Default balanced profile. */
	public static FishBotConfig defaults() {
		return new FishBotConfig();
	}

	/**
	 * Nocny AFK — overnight unattended fishing: safety systems on,
	 * slower, more human-like timings, auto deposit and auto eat.
	 */
	public static FishBotConfig overnightAfk() {
		FishBotConfig c = defaults();
		c.reelInDelayMinMs = 200;
		c.reelInDelayMaxMs = 450;
		c.recastDelayMs = 2000;
		c.recastJitterPercent = 25;
		c.persistentMode = true;
		c.multiRod = true;
		c.rodDurabilityThreshold = 10;
		c.mendingOffhand = true;
		c.autoDeposit = true;
		c.depositTriggerSlots = 30;
		c.discardTrash = true;
		c.autoEat = true;
		c.eatThreshold = 14;
		c.panicEnabled = true;
		c.panicHealthPercent = 30;
		c.panicOnDamage = true;
		c.panicAction = FishBotConfig.PanicAction.DISCONNECT;
		c.antiAfk = true;
		c.antiAfkMinSec = 40;
		c.antiAfkMaxSec = 90;
		c.hudEnabled = true;
		return c;
	}

	/**
	 * Łowca skarbów — treasure-focused: strict open-water checking and
	 * depositing only treasure loot.
	 */
	public static FishBotConfig treasureHunter() {
		FishBotConfig c = overnightAfk();
		c.openWaterDetection = true;
		c.depositMode = FishBotConfig.DepositMode.TREASURES;
		c.discardTrash = true;
		c.discordOnRare = true;
		return c;
	}

	/**
	 * Szybkie łowienie — minimal delays for maximum catches per hour.
	 * Less human-like; use on servers where allowed.
	 */
	public static FishBotConfig fastFishing() {
		FishBotConfig c = defaults();
		c.reelInDelayMinMs = 80;
		c.reelInDelayMaxMs = 160;
		c.recastDelayMs = 900;
		c.recastJitterPercent = 5;
		c.openWaterDetection = true;
		c.multiRod = true;
		c.rodDurabilityThreshold = 6;
		c.autoDeposit = true;
		c.depositTriggerSlots = 32;
		c.autoEat = true;
		c.panicEnabled = true;
		c.antiAfk = false;
		return c;
	}
}
