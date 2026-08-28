package dev.fishbot.fishing;

import dev.fishbot.config.FishBotConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Bite detection. Three interchangeable strategies:
 *
 * <ul>
 *   <li><b>PACKET</b> (default) — reads the bobber's synced entity data
 *       ({@code biting} flag, set server-side the very tick a fish bites)
 *       plus motion packets as confirmation. Effectively 100% reliable and
 *       reacts within a single network packet.</li>
 *   <li><b>MOTION</b> — classic multiplayer detection based on the
 *       {@code SetEntityMotion} packet pulling the bobber down.</li>
 *   <li><b>SOUND</b> — the splash sound played at the bobber.</li>
 * </ul>
 */
public class FishMonitor {

	// Motion detection tuning (from classic autofish experience).
	private static final double MOTION_Y_THRESHOLD = -0.1;
	private static final long MIN_TIME_IN_WATER_MS = 1000;
	// Sound detection: bobber must be within 4 blocks of the splash.
	private static final double SOUND_DISTANCE_SQ_THRESHOLD = 16.0;

	private final FishingCore core;

	// Motion detection state.
	private boolean hasHitWater = false;
	private long bobberRiseTimestamp = 0;

	// Debounce: once a bite was handled, ignore everything until the hook is gone.
	private boolean biteHandled = false;

	public FishMonitor(FishingCore core) {
		this.core = core;
	}

	// ------------------------------------------------------------------
	// PACKET mode: synced entity data ("biting" flag)
	// ------------------------------------------------------------------

	/** Called from {@code FishingHookMixin} when synced entity data changes. */
	public void onHookDataUpdated(net.minecraft.world.entity.projectile.FishingHook hook, boolean biting) {
		if (!isPacketMode()) {
			return;
		}
		if (!biting || biteHandled) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.fishing != hook) {
			return;
		}
		biteHandled = true;
		core.onBiteDetected();
	}

	private boolean isPacketMode() {
		return core.getConfig().detectionMode == FishBotConfig.DetectionMode.PACKET;
	}

	// ------------------------------------------------------------------
	// MOTION / SOUND modes: packets
	// ------------------------------------------------------------------

	/** Called from {@code ClientPacketListenerMixin} for every play packet. */
	public void handlePacket(Packet<?> packet) {
		FishBotConfig config = core.getConfig();
		if (config.detectionMode == FishBotConfig.DetectionMode.MOTION && packet instanceof ClientboundSetEntityMotionPacket motion) {
			handleMotionPacket(motion);
		} else if (config.detectionMode == FishBotConfig.DetectionMode.SOUND && packet instanceof ClientboundSoundPacket sound) {
			handleSoundPacket(sound);
		}
	}

	private void handleMotionPacket(ClientboundSetEntityMotionPacket packet) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.fishing == null || biteHandled) {
			return;
		}
		if (player.fishing.getId() != packet.id()) {
			return;
		}

		// Wait until the bobber actually sits in water.
		if (hasHitWater && bobberRiseTimestamp == 0 && packet.movement().y > 0) {
			bobberRiseTimestamp = core.timeMillis();
		}

		long timeInWater = core.timeMillis() - bobberRiseTimestamp;
		if (hasHitWater && bobberRiseTimestamp != 0 && timeInWater > MIN_TIME_IN_WATER_MS) {
			if (packet.movement().x == 0.0 && packet.movement().z == 0.0 && packet.movement().y < MOTION_Y_THRESHOLD) {
				biteHandled = true;
				core.onBiteDetected();
			}
		}
	}

	private void handleSoundPacket(ClientboundSoundPacket packet) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || player.fishing == null || biteHandled) {
			return;
		}
		if (!packet.getSound().value().equals(SoundEvents.FISHING_BOBBER_SPLASH)) {
			return;
		}
		double distSq = player.fishing.distanceToSqr(packet.getX(), packet.getY(), packet.getZ());
		if (distSq <= SOUND_DISTANCE_SQ_THRESHOLD) {
			biteHandled = true;
			core.onBiteDetected();
		}
	}

	// ------------------------------------------------------------------
	// Per-tick helpers
	// ------------------------------------------------------------------

	/** Track whether the bobber has landed in water (motion mode). */
	public void hookTick(net.minecraft.world.entity.projectile.FishingHook hook) {
		if (hookInWater(hook.level(), hook.getBoundingBox())) {
			hasHitWater = true;
		}
	}

	/** Reset detection state — called whenever the hook disappears. */
	public void handleHookRemoved() {
		hasHitWater = false;
		bobberRiseTimestamp = 0;
		biteHandled = false;
	}

	private static boolean hookInWater(Level level, AABB box) {
		int minX = Mth.floor(box.minX);
		int maxX = Mth.ceil(box.maxX);
		int minY = Mth.floor(box.minY);
		int maxY = Mth.ceil(box.maxY);
		int minZ = Mth.floor(box.minZ);
		int maxZ = Mth.ceil(box.maxZ);
		return BlockPos.betweenClosedStream(minX, minY, minZ, maxX - 1, maxY - 1, maxZ - 1)
				.anyMatch(pos -> level.getBlockState(pos).getBlock() == Blocks.WATER);
	}
}
