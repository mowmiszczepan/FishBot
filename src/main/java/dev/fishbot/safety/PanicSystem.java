package dev.fishbot.safety;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Overnight-AFK failsafe. Watches the player's health and reacts to
 * damage (mobs, players, lava...) with a configurable action:
 * alert only, disconnect, or throw an ender pearl before disconnecting.
 */
public class PanicSystem {

	private static final long ATTACK_ALERT_COOLDOWN_MS = 10_000;
	private static final long ALERT_ONLY_GRACE_MS = 5000;
	private static final long PEARL_RECHECK_MS = 3000;

	private final FishBotCore bot;

	private float previousHealth = Float.NaN;
	private boolean panicking = false;
	private long lastAttackAlertAt = 0;

	public PanicSystem(FishBotCore bot) {
		this.bot = bot;
	}

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			previousHealth = Float.NaN;
			return;
		}

		float health = player.getHealth();
		float maxHealth = player.getMaxHealth();
		boolean damaged = !Float.isNaN(previousHealth) && health < previousHealth - 0.001F;
		previousHealth = health;

		FishBotConfig config = bot.getConfig();
		if (!config.enabled || !config.panicEnabled) {
			return;
		}

		// Chat/Discord notice for every hit.
		if (damaged && !panicking && Util.getMillis() - lastAttackAlertAt > ATTACK_ALERT_COOLDOWN_MS) {
			lastAttackAlertAt = Util.getMillis();
			player.sendSystemMessage(Component.translatable("fishbot.chat.attacked"));
			if (config.discordEnabled && config.discordOnAttack) {
				bot.getDiscord().notify(
						Component.translatable("fishbot.discord.attacked", Math.round(health)).getString(), 0xE74C3C);
			}
		}

		if (panicking) {
			return;
		}

		boolean healthLow = maxHealth > 0 && (health / maxHealth) * 100.0F <= config.panicHealthPercent;
		boolean trigger = healthLow || (config.panicOnDamage && damaged);
		if (trigger) {
			doPanic(mc, health, maxHealth);
		}
	}

	private void doPanic(Minecraft mc, float health, float maxHealth) {
		FishBotConfig config = bot.getConfig();
		panicking = true;

		// Stop all automation before acting.
		bot.getFishing().haltForPanic();
		bot.getEater().forceStop();
		bot.getChestManager().abort();

		LocalPlayer player = mc.player;
		if (player != null) {
			player.sendSystemMessage(Component.translatable("fishbot.chat.panic"));
		}
		if (config.discordEnabled && config.discordOnAttack) {
			bot.getDiscord().notify(
					Component.translatable("fishbot.discord.panic", Math.round(health), Math.round(maxHealth)).getString(),
					0xE67E22);
		}

		switch (config.panicAction) {
			case ALERT_ONLY -> bot.getScheduler().schedule(ALERT_ONLY_GRACE_MS, () -> {
				panicking = false;
				bot.getFishing().resumeAfterPanic();
			});
			case DISCONNECT -> bot.getScheduler().schedule(400, () -> disconnect(mc, "fishbot.panic.disconnect"));
			case PEARL_THEN_DISCONNECT -> {
				throwEnderPearl(mc);
				bot.getScheduler().schedule(PEARL_RECHECK_MS, () -> {
					LocalPlayer p = mc.player;
					float max = p != null ? p.getMaxHealth() : maxHealth;
					boolean stillDanger = p != null && max > 0
							&& (p.getHealth() / max) * 100.0F <= config.panicHealthPercent;
					if (stillDanger) {
						disconnect(mc, "fishbot.panic.disconnect");
					} else {
						// Teleported away / danger over: resume.
						panicking = false;
						bot.getFishing().resumeAfterPanic();
					}
				});
			}
		}
	}

	private boolean throwEnderPearl(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gameMode == null) {
			return false;
		}
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() == Items.ENDER_PEARL) {
				player.getInventory().setSelectedSlot(i);
				mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
				return true;
			}
		}
		return false;
	}

	private void disconnect(Minecraft mc, String reasonKey) {
		FishBotConfig config = bot.getConfig();
		Component reason = Component.translatable(reasonKey);
		if (config.discordEnabled && config.discordOnAttack) {
			bot.getDiscord().notify(reason.getString(), 0xE74C3C);
		}
		if (mc.player != null && mc.player.connection != null) {
			// getConnection().disconnect() actually closes the TCP socket; the
			// onDisconnect callback only informs the client of a disconnect.
			mc.player.connection.getConnection().disconnect(reason);
		}
	}

	/** Fishing and other automation stay halted while panicking. */
	public boolean isHalted() {
		return panicking;
	}

	/** World change / logout. */
	public void reset() {
		previousHealth = Float.NaN;
		panicking = false;
	}
}
