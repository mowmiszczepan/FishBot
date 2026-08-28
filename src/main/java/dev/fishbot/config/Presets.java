package dev.fishbot.config;

/**
 * Built-in configuration profiles. Only the default balanced profile remains;
 * specialised profiles are removed so users manage their own custom presets.
 */
public final class Presets {

	private Presets() {
	}

	/** Default balanced profile. */
	public static FishBotConfig defaults() {
		return new FishBotConfig();
	}
}
