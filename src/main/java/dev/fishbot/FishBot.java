package dev.fishbot;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import dev.fishbot.gui.FishBotScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * FishBot — advanced fishing automation for Minecraft 26.2.
 * Client entry point: registers key bindings, the HUD and the main tick.
 */
public class FishBot implements ClientModInitializer {

	public static final String MOD_ID = "fishbot";

	private static FishBot instance;

	private FishBotCore core;
	private KeyMapping openGuiKey;
	private KeyMapping toggleKey;

	@Override
	public void onInitializeClient() {
		instance = this;
		this.core = new FishBotCore();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "fishbot"));

		openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"fishbot.key.open_gui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				category));

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"fishbot.key.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
	}

	private void onClientTick(Minecraft mc) {
		// Always consume clicks so presses from menus don't pile up.
		boolean openGui = openGuiKey.consumeClick();
		boolean toggle = toggleKey.consumeClick();

		if (mc.player == null) {
			core.tick(mc); // still cleans up world state
			return;
		}

		if (openGui) {
			mc.setScreenAndShow(new FishBotScreen(Component.translatable("fishbot.gui.title"),
					mc.gui.screen(), core));
		}
		if (toggle) {
			core.toggleEnabled(mc);
		}

		core.tick(mc);
	}

	public static FishBot getInstance() {
		return instance;
	}

	public FishBotCore getCore() {
		return core;
	}
}
