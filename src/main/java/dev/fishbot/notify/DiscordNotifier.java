package dev.fishbot.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.fishbot.FishBotCore;
import dev.fishbot.config.FishBotConfig;

/**
 * Discord webhook integration. Notifications are sent asynchronously on a
 * daemon thread and never block the game. Rate-limited to avoid spamming
 * the webhook when something goes wrong.
 */
public class DiscordNotifier {

	private static final long MIN_INTERVAL_MS = 2500;
	private static final String USERNAME = "FishBot";

	private final FishBotCore bot;
	private final HttpClient http;
	private final ExecutorService executor;
	private volatile long lastSentAt = 0;

	public DiscordNotifier(FishBotCore bot) {
		this.bot = bot;
		this.http = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		this.executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "FishBot-Discord");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Send a simple colored embed.
	 *
	 * @param message embed title/description text
	 * @param color   RGB color, e.g. 0xE74C3C
	 */
	public void notify(String message, int color) {
		FishBotConfig config = bot.getConfig();
		if (!config.discordEnabled || !isValidWebhook(config.discordWebhookUrl)) {
			return;
		}
		executor.submit(() -> {
			try {
				long now = System.currentTimeMillis();
				if (now - lastSentAt < MIN_INTERVAL_MS) {
					return;
				}
				lastSentAt = now;

				JsonObject embed = new JsonObject();
				embed.addProperty("title", "FishBot");
				embed.addProperty("description", message);
				embed.addProperty("color", color);
				embed.addProperty("timestamp", Instant.now().toString());

				JsonObject payload = new JsonObject();
				payload.addProperty("username", USERNAME);
				JsonArray embeds = new JsonArray();
				embeds.add(embed);
				payload.add("embeds", embeds);

				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(config.discordWebhookUrl))
						.timeout(Duration.ofSeconds(6))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
						.build();
				http.send(request, HttpResponse.BodyHandlers.discarding());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Throwable t) {
				System.err.println("[FishBot] Discord webhook failed: " + t.getMessage());
			}
		});
	}

	/** Rare catch alert with the localized item name. */
	public void notifyRareCatch(String itemName) {
		FishBotConfig config = bot.getConfig();
		if (!config.discordOnRare) {
			return;
		}
		notify(net.minecraft.network.chat.Component.translatable("fishbot.discord.rare", itemName).getString(),
				0xF1C40F);
	}

	public static boolean isValidWebhook(String url) {
		if (url == null || url.isBlank()) {
			return false;
		}
		return url.startsWith("https://discord.com/api/webhooks/")
				|| url.startsWith("https://discordapp.com/api/webhooks/")
				|| url.startsWith("https://ptb.discord.com/api/webhooks/")
				|| url.startsWith("https://canary.discord.com/api/webhooks/");
	}

	/** Shut down the worker thread (client shutdown). */
	public void shutdown() {
		executor.shutdownNow();
	}
}
