package dev.fishbot.config;

import java.util.ArrayList;
import java.util.List;

/**
 * All FishBot settings. Serialized to {@code config/fishbot.json} with Gson.
 *
 * <p>Every field is public with a sane default so Gson can (de)serialize it
 * without getters/setters, and so presets can copy values field-by-field.
 */
public class FishBotConfig {

	// ------------------------------------------------------------------
	// Master switch
	// ------------------------------------------------------------------
	public boolean enabled = true;

	// ------------------------------------------------------------------
	// Fishing mechanics
	// ------------------------------------------------------------------

	/** How bites are detected. PACKET reads synced entity data / packets. */
	public DetectionMode detectionMode = DetectionMode.PACKET;

	/** Validate the 1.16+ open-water (5x4x5) treasure conditions. */
	public boolean openWaterDetection = true;

	/** Keep the hook cast; re-cast automatically if it disappears. */
	public boolean persistentMode = true;

	/** Swing the arm when using the rod (looks more human). */
	public boolean armSwing = true;

	// ------------------------------------------------------------------
	// Humanization
	// ------------------------------------------------------------------

	/** Randomized delay after a bite before reeling in (min, ms). */
	public int reelInDelayMinMs = 150;
	/** Randomized delay after a bite before reeling in (max, ms). */
	public int reelInDelayMaxMs = 350;

	/** Delay between landing a catch and casting again (ms). */
	public long recastDelayMs = 1500;
	/** Randomness added to the recast delay, in percent (0 disables). */
	public int recastJitterPercent = 20;

	// ------------------------------------------------------------------
	// Rods & Mending
	// ------------------------------------------------------------------

	/** Cycle through hotbar rods when the active one wears out. */
	public boolean multiRod = true;

	/** Stop using a rod before it would break. */
	public boolean breakProtection = true;

	/** Switch rods when remaining durability drops to this value. */
	public int rodDurabilityThreshold = 10;

	/**
	 * Move a Mending rod to the off-hand while experience orbs are being
	 * absorbed so its durability regenerates.
	 */
	public boolean mendingOffhand = true;

	// ------------------------------------------------------------------
	// Inventory management (chest deposit)
	// ------------------------------------------------------------------

	/** Automatically deposit loot into a nearby chest/shulker when full. */
	public boolean autoDeposit = true;

	/** Search radius (blocks) for a chest/shulker box. */
	public int depositRadius = 4;

	/** Trigger the deposit when at least this many inventory slots are used. */
	public int depositTriggerSlots = 30;

	/** Which items are moved into the chest. */
	public DepositMode depositMode = DepositMode.KEEP_LIST;

	/** With KEEP_LIST: everything is deposited EXCEPT these item ids. */
	public List<String> keepItems = new ArrayList<>(List.of(
			"minecraft:fishing_rod",
			"minecraft:ender_pearl"
	));

	/** With TREASURES: only these item ids are deposited. */
	public List<String> treasureItems = new ArrayList<>(List.of(
			"minecraft:enchanted_book",
			"minecraft:saddle",
			"minecraft:name_tag",
			"minecraft:nautilus_shell",
			"minecraft:bow",
			"minecraft:fishing_rod"
	));

	/** Item ids treated as trash (thrown away when discardTrash is on). */
	public List<String> trashItems = new ArrayList<>(List.of(
			"minecraft:leather_boots",
			"minecraft:bowl",
			"minecraft:leather",
			"minecraft:stick",
			"minecraft:string",
			"minecraft:rotten_flesh",
			"minecraft:ink_sac",
			"minecraft:lily_pad",
			"minecraft:bone",
			"minecraft:tripwire_hook"
	));

	/** Throw trash items away instead of storing them. */
	public boolean discardTrash = true;

	// ------------------------------------------------------------------
	// Auto eat
	// ------------------------------------------------------------------

	/** Eat automatically from the hotbar. */
	public boolean autoEat = true;

	/** Start eating when the hunger bar drops to this value (of 20). */
	public int eatThreshold = 14;

	// ------------------------------------------------------------------
	// Safety (overnight AFK)
	// ------------------------------------------------------------------

	/** Panic system master switch. */
	public boolean panicEnabled = true;

	/** Panic when health drops below this percentage of max health. */
	public int panicHealthPercent = 30;

	/** Panic whenever any damage is taken (mob, player, lava...). */
	public boolean panicOnDamage = true;

	/** What the panic system does. */
	public PanicAction panicAction = PanicAction.DISCONNECT;

	// ------------------------------------------------------------------
	// Anti-AFK
	// ------------------------------------------------------------------

	/** Subtle, irregular movements / key taps to bypass AFK detection. */
	public boolean antiAfk = true;

	/** Minimum pause between anti-AFK micro-actions (seconds). */
	public int antiAfkMinSec = 40;
	/** Maximum pause between anti-AFK micro-actions (seconds). */
	public int antiAfkMaxSec = 90;

	// ------------------------------------------------------------------
	// HUD
	// ------------------------------------------------------------------

	/** Draw the live statistics overlay. */
	public boolean hudEnabled = true;
	public int hudX = 4;
	public int hudY = 4;

	// ------------------------------------------------------------------
	// Discord webhook
	// ------------------------------------------------------------------

	public boolean discordEnabled = false;
	public String discordWebhookUrl = "";
	/** Notify on rare catches (treasure list + rarity). */
	public boolean discordOnRare = true;
	/** Notify when storage is full / deposit happened. */
	public boolean discordOnStorage = true;
	/** Notify when the player is attacked / panic triggers. */
	public boolean discordOnAttack = true;

	/** Item ids that count as "rare" for HUD stats and Discord alerts. */
	public List<String> rareItems = new ArrayList<>(List.of(
			"minecraft:enchanted_book",
			"minecraft:bow",
			"minecraft:fishing_rod"
	));

	// ------------------------------------------------------------------
	// Presets & sessions
	// ------------------------------------------------------------------

	/** Name of the currently-active preset/session, shown in the HUD. */
	public String activePreset;

	// ------------------------------------------------------------------
	// Behaviour
	// ------------------------------------------------------------------

	/** Pause the bot while a container screen is open manually. */
	public boolean pauseInGui = true;

	public enum DetectionMode {
		/** Synced entity data (biting flag) + motion packets. Works everywhere. */
		PACKET,
		/** Bobber velocity packets only (classic multiplayer detection). */
		MOTION,
		/** Splash sound near the bobber. */
		SOUND
	}

	public enum DepositMode {
		/** Deposit everything except {@link #keepItems}. */
		KEEP_LIST,
		/** Deposit only items in {@link #treasureItems}. */
		TREASURES
	}

	public enum PanicAction {
		/** Only send alerts (Discord/chat), stay connected. */
		ALERT_ONLY,
		/** Disconnect from the server. */
		DISCONNECT,
		/** Throw an ender pearl, then disconnect if still in danger. */
		PEARL_THEN_DISCONNECT
	}
}
