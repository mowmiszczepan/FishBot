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
	public long durationMs;

	/** Fish caught in that session. */
	public int catches;

	/** Experience gained in that session. */
	public int xpGained;

	/** Rare drops (enchanted books / bows / rods) caught in that session. */
	public int rareCatches;

	/** The settings snapshot so applying the session restores them. */
	public FishBotConfig config;

	public FishBotSession() {
	}

	/** Full constructor for all statistics fields. */
	public FishBotSession(String name, long savedAt, long durationMs,
			int catches, int xpGained, int rareCatches, FishBotConfig config) {
		this.name = name;
		this.savedAt = savedAt;
		this.durationMs = durationMs;
		this.catches = catches;
		this.xpGained = xpGained;
		this.rareCatches = rareCatches;
		this.config = config;
	}
}
