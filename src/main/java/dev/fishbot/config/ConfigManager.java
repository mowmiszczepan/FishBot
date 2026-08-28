package dev.fishbot.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads/saves {@link FishBotConfig} as JSON and manages preset profiles.
 */
public class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path configPath;
	private final Path presetsDir;
	private final Path sessionsDir;
	private FishBotConfig config;

	public ConfigManager() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		this.configPath = configDir.resolve("fishbot.json");
		this.presetsDir = configDir.resolve("fishbot_presets");
		this.sessionsDir = configDir.resolve("fishbot_sessions");
		this.config = load();
	}

	public FishBotConfig getConfig() {
		return config;
	}

	/** Replace the active config (used when applying a preset). */
	public void setConfig(FishBotConfig newConfig) {
		this.config = newConfig;
		save();
	}

	public synchronized FishBotConfig load() {
		try {
			if (Files.exists(configPath)) {
				String json = Files.readString(configPath, StandardCharsets.UTF_8);
				FishBotConfig loaded = GSON.fromJson(json, FishBotConfig.class);
				if (loaded != null) {
					return normalize(loaded);
				}
			}
		} catch (Exception e) {
			System.err.println("[FishBot] Failed to load config, using defaults: " + e);
		}
		return new FishBotConfig();
	}

	/** Guard against partially-initialized fields after (de)serialization. */
	private static FishBotConfig normalize(FishBotConfig loaded) {
		if (loaded.keepItems == null) loaded.keepItems = new ArrayList<>();
		if (loaded.treasureItems == null) loaded.treasureItems = new ArrayList<>();
		if (loaded.trashItems == null) loaded.trashItems = new ArrayList<>();
		if (loaded.rareItems == null) loaded.rareItems = new ArrayList<>();
		if (loaded.discordWebhookUrl == null) loaded.discordWebhookUrl = "";
		if (loaded.detectionMode == null) loaded.detectionMode = FishBotConfig.DetectionMode.PACKET;
		if (loaded.depositMode == null) loaded.depositMode = FishBotConfig.DepositMode.KEEP_LIST;
		if (loaded.panicAction == null) loaded.panicAction = FishBotConfig.PanicAction.DISCONNECT;
		return loaded;
	}

	public synchronized void save() {
		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, GSON.toJson(config), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[FishBot] Failed to save config: " + e);
		}
	}

	// ------------------------------------------------------------------
	// Presets
	// ------------------------------------------------------------------

	/** Names of custom presets stored in config/fishbot_presets/. */
	public List<String> listCustomPresets() {
		return listJsonNames(presetsDir);
	}

	/** Remove a custom preset file. */
	public void deleteCustomPreset(String name) {
		deleteJson(presetsDir, name);
	}

	// ------------------------------------------------------------------
	// Sessions
	// ------------------------------------------------------------------

	/** Names of saved sessions stored in config/fishbot_sessions/. */
	public List<String> listSessions() {
		return listJsonNames(sessionsDir);
	}

	public void saveSession(FishBotSession session) {
		try {
			Files.createDirectories(sessionsDir);
			Files.writeString(sessionsDir.resolve(sanitize(session.name) + ".json"),
					GSON.toJson(session), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[FishBot] Failed to save session '" + session.name + "': " + e);
		}
	}

	public FishBotSession loadSession(String name) {
		try {
			Path file = sessionsDir.resolve(sanitize(name) + ".json");
			if (Files.exists(file)) {
				return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FishBotSession.class);
			}
		} catch (Exception e) {
			System.err.println("[FishBot] Failed to load session '" + name + "': " + e);
		}
		return null;
	}

	public void deleteSession(String name) {
		deleteJson(sessionsDir, name);
	}

	private static List<String> listJsonNames(Path dir) {
		List<String> names = new ArrayList<>();
		try {
			if (Files.isDirectory(dir)) {
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
					for (Path p : stream) {
						String name = p.getFileName().toString();
						names.add(name.substring(0, name.length() - 5));
					}
				}
			}
		} catch (IOException ignored) {
		}
		Collections.sort(names);
		return names;
	}

	private static void deleteJson(Path dir, String name) {
		try {
			Files.deleteIfExists(dir.resolve(sanitize(name) + ".json"));
		} catch (IOException e) {
			System.err.println("[FishBot] Failed to delete '" + name + "': " + e);
		}
	}

	public FishBotConfig loadCustomPreset(String name) {
		try {
			Path file = presetsDir.resolve(name + ".json");
			if (Files.exists(file)) {
				FishBotConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FishBotConfig.class);
				return loaded == null ? null : normalize(loaded);
			}
		} catch (Exception e) {
			System.err.println("[FishBot] Failed to load preset '" + name + "': " + e);
		}
		return null;
	}

	public void saveCustomPreset(String name, FishBotConfig preset) {
		try {
			Files.createDirectories(presetsDir);
			Files.writeString(presetsDir.resolve(sanitize(name) + ".json"), GSON.toJson(preset), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[FishBot] Failed to save preset '" + name + "': " + e);
		}
	}

	private static String sanitize(String name) {
		StringBuilder sb = new StringBuilder();
		for (char c : name.toCharArray()) {
			if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
				sb.append(c);
			}
		}
		return sb.isEmpty() ? "preset" : sb.toString();
	}

	/** Deep copy via JSON round-trip (config is plain data). */
	public static FishBotConfig copy(FishBotConfig source) {
		return GSON.fromJson(GSON.toJson(source), FishBotConfig.class);
	}
}
