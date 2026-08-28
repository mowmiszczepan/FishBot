package dev.fishbot.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.fishbot.FishBot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

/**
 * Forwards incoming game packets to the bite monitors (MOTION / SOUND
 * detection modes and general awareness).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl
		implements TickablePacketListener, ClientGamePacketListener {

	protected ClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie cookie) {
		super(minecraft, connection, cookie);
	}

	@Inject(method = "handleSoundEvent", at = @At("HEAD"))
	private void fishbot$onSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
		if (minecraft.isSameThread()) {
			FishBot fishBot = FishBot.getInstance();
			if (fishBot != null) {
				fishBot.getCore().getFishing().getMonitor().handlePacket(packet);
			}
		}
	}

	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"))
	private void fishbot$onEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		if (minecraft.isSameThread()) {
			FishBot fishBot = FishBot.getInstance();
			if (fishBot != null) {
				fishBot.getCore().getFishing().getMonitor().handlePacket(packet);
			}
		}
	}
}
