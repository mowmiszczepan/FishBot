package dev.fishbot.safety;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;
import dev.fishbot.fishing.ActionScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/**
 * Anti-AFK bypass: subtle, irregular micro-movements — tiny camera
 * adjustments, occasional sneak/jump/strafe taps at randomized intervals.
 */
public class AntiAfk {

	private enum MicroAction {
		LOOK, SNEAK, JUMP, STRAFE
	}

	private static final class Active {
		final MicroAction type;
		final long endsAt;
		final float savedYaw;
		final float savedPitch;

		Active(MicroAction type, long endsAt, float savedYaw, float savedPitch) {
			this.type = type;
			this.endsAt = endsAt;
			this.savedYaw = savedYaw;
			this.savedPitch = savedPitch;
		}
	}

	private final FishBotCore bot;

	private long nextActionAt = 0;
	private Active active = null;

	public AntiAfk(FishBotCore bot) {
		this.bot = bot;
	}

	public void tick(Minecraft mc) {
		FishBotConfig config = bot.getConfig();
		LocalPlayer player = mc.player;
		if (!config.enabled || !config.antiAfk || player == null || mc.level == null) {
			endActive(mc);
			return;
		}
		// Don't interfere with deliberate GUI/chest interaction.
		if (bot.isScreenOpen() || bot.getChestManager().isControllingRotation()) {
			return;
		}

		long now = Util.getMillis();

		// Finish an in-flight micro action.
		if (active != null) {
			if (now >= active.endsAt) {
				endActive(mc);
			}
			return;
		}

		if (nextActionAt == 0) {
			nextActionAt = now + nextInterval(config);
			return;
		}
		if (now >= nextActionAt) {
			startRandomAction(mc, player, now);
			nextActionAt = now + nextInterval(config);
		}
	}

	private long nextInterval(FishBotConfig config) {
		int minSec = Math.max(5, Math.min(config.antiAfkMinSec, config.antiAfkMaxSec));
		int maxSec = Math.max(minSec, Math.max(config.antiAfkMinSec, config.antiAfkMaxSec));
		return ActionScheduler.randomDelay(minSec, maxSec) * 1000L;
	}

	private void startRandomAction(Minecraft mc, LocalPlayer player, long now) {
		MicroAction type = pickAction();
		switch (type) {
			case LOOK -> {
				float deltaYaw = (float) ((ActionScheduler.RANDOM.nextDouble() * 2 - 1) * 4.0);
				float deltaPitch = (float) ((ActionScheduler.RANDOM.nextDouble() * 2 - 1) * 2.0);
				active = new Active(type, now + ActionScheduler.randomDelay(200, 600),
						player.getYRot(), player.getXRot());
				player.setYRot(player.getYRot() + deltaYaw);
				player.setXRot(net.minecraft.util.Mth.clamp(player.getXRot() + deltaPitch, -90.0F, 90.0F));
			}
			case SNEAK -> {
				mc.options.keyShift.setDown(true);
				active = new Active(type, now + ActionScheduler.randomDelay(250, 650), 0, 0);
			}
			case JUMP -> {
				mc.options.keyJump.setDown(true);
				active = new Active(type, now + ActionScheduler.randomDelay(80, 160), 0, 0);
			}
			case STRAFE -> {
				if (ActionScheduler.RANDOM.nextBoolean()) {
					mc.options.keyLeft.setDown(true);
				} else {
					mc.options.keyRight.setDown(true);
				}
				active = new Active(type, now + ActionScheduler.randomDelay(100, 300), 0, 0);
			}
		}
	}

	private MicroAction pickAction() {
		MicroAction[] values = MicroAction.values();
		return values[ActionScheduler.RANDOM.nextInt(values.length)];
	}

	private void endActive(Minecraft mc) {
		if (active == null) {
			return;
		}
		LocalPlayer player = mc.player;
		switch (active.type) {
			case LOOK -> {
				if (player != null) {
					// Restore the original view with a tiny human-like offset.
					player.setYRot(active.savedYaw);
					player.setXRot(active.savedPitch);
				}
			}
			case SNEAK -> mc.options.keyShift.setDown(false);
			case JUMP -> mc.options.keyJump.setDown(false);
			case STRAFE -> {
				mc.options.keyLeft.setDown(false);
				mc.options.keyRight.setDown(false);
			}
		}
		active = null;
	}
}
