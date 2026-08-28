package dev.fishbot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fishbot.FishBot;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Packet-based bite detection.
 *
 * <p>When a fish bites, the server flips the bobber's synced {@code biting}
 * entity-data flag. This arrives as a {@code SetEntityData} packet and fires
 * {@code onSyncedDataUpdated} on the client — a 100% reliable, sub-tick
 * bite signal for both singleplayer and multiplayer.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

	@Shadow
	private boolean biting;

	@Shadow
	private static EntityDataAccessor<Boolean> DATA_BITING;

	@Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
	private void fishbot$onEntityDataUpdated(EntityDataAccessor<?> accessor, CallbackInfo ci) {
		// Only react when the specific synced-data key that represents a bite
		// changed; other entity-data updates (attaching, gravity, ...) must not
		// re-trigger the reeling procedure even if the biting flag is set.
		if (!DATA_BITING.equals(accessor) || !this.biting) {
			return;
		}
		FishingHook self = (FishingHook) (Object) this;
		if (!self.level().isClientSide()) {
			return;
		}
		FishBot fishBot = FishBot.getInstance();
		if (fishBot != null) {
			fishBot.getCore().getFishing().getMonitor().onHookDataUpdated(self, true);
		}
	}
}
