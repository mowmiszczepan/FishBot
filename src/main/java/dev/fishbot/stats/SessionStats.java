package dev.fishbot.stats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/**
 * Live session statistics: time, casts, catches, rare catches, deposits
 * and experience gained (for the HUD and Discord summaries).
 */
public class SessionStats {

	private long sessionStart = Util.getMillis();
	private int casts;
	private int catches;
	private int rareCatches;
	private int deposits;

	private int xpBaseline = -1;
	private int xpGained;

	public void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		int xp = player.totalExperience;
		if (xpBaseline < 0) {
			xpBaseline = xp;
		} else if (xp < xpBaseline) {
			// Death / respawn resets the counter.
			xpBaseline = xp;
		}
		xpGained = xp - xpBaseline;
	}

	public void onCast() {
		// Start the session clock on the first cast so the per-hour figures
		// aren't diluted by idle time spent before fishing actually began.
		if (casts == 0) {
			sessionStart = Util.getMillis();
		}
		casts++;
	}

	public void onCatch() {
		catches++;
	}

	public void onRareCatch() {
		rareCatches++;
	}

	public void onDeposit() {
		deposits++;
	}

	// ------------------------------------------------------------------

	public long sessionMillis() {
		return Util.getMillis() - sessionStart;
	}

	public int getCasts() {
		return casts;
	}

	public int getCatches() {
		return catches;
	}

	public int getRareCatches() {
		return rareCatches;
	}

	public int getDeposits() {
		return deposits;
	}

	public int getXpGained() {
		return Math.max(0, xpGained);
	}

	public double catchesPerHour() {
		double hours = sessionMillis() / 3_600_000.0;
		return hours > 0 ? catches / hours : 0;
	}

	public double xpPerHour() {
		double hours = sessionMillis() / 3_600_000.0;
		return hours > 0 ? getXpGained() / hours : 0;
	}

	public void reset() {
		sessionStart = Util.getMillis();
		casts = 0;
		catches = 0;
		rareCatches = 0;
		deposits = 0;
		xpBaseline = -1;
		xpGained = 0;
	}

	public static String formatDuration(long millis) {
		long totalSeconds = millis / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}
}
