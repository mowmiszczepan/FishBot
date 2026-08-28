package dev.fishbot.config;

/**
 * A saved session snapshot: the statistics accumulated during a fishing run
 * together with the configuration that was active at the moment the session
 * was created. Serialized to {@code config/fishbot_sessions/} with Gson.
 */
public class FishBotSession {

	/** Display name used to reference / delete the session. */
	public String name;

	/** Wall-clock time the session was saved (millis). */
	public long savedAt;

	/** How long the fishing session had been running when it was saved. */
	public long sessionMillis;

	/** Fish caught in that session. */
	public int catches;

	/** Experience gained in that session. */
	public int xp;

	/** Rare drops (enchanted books / bows / rods) caught in that session. */
	public int rareCatches;

	/** The settings snapshot so applying the session restores them. */
	public FishBotConfig config;
}
