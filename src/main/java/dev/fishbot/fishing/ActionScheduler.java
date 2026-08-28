package dev.fishbot.fishing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.util.Util;

/**
 * Tiny client-thread scheduler for delayed/repeating actions with
 * human-like timing. Only ever called from the client tick event.
 */
public class ActionScheduler {
	public static final Random RANDOM = new Random();

	private final List<Scheduled> queue = new ArrayList<>();

	private static final class Scheduled {
		long runAt;
		final long interval;
		final boolean repeating;
		final Runnable action;

		Scheduled(long runAt, long interval, boolean repeating, Runnable action) {
			this.runAt = runAt;
			this.interval = interval;
			this.repeating = repeating;
			this.action = action;
		}
	}

	/** Run {@code action} once after {@code delayMs} milliseconds. */
	public void schedule(long delayMs, Runnable action) {
		queue.add(new Scheduled(Util.getMillis() + delayMs, 0, false, action));
	}

	/** Run {@code action} every {@code intervalMs} until cancelled via the returned handle. */
	public ScheduledHandle scheduleRepeating(long intervalMs, Runnable action) {
		Scheduled s = new Scheduled(Util.getMillis() + intervalMs, intervalMs, true, action);
		queue.add(s);
		return new ScheduledHandle(s);
	}

	public void tick() {
		long now = Util.getMillis();
		List<Runnable> toRun = new ArrayList<>();
		Iterator<Scheduled> it = queue.iterator();
		while (it.hasNext()) {
			Scheduled s = it.next();
			if (now >= s.runAt) {
				if (s.repeating) {
					s.runAt = now + s.interval;
				} else {
					it.remove();
				}
				toRun.add(s.action);
			}
		}
		for (Runnable r : toRun) {
			try {
				r.run();
			} catch (Throwable t) {
				System.err.println("[FishBot] Scheduled action failed: " + t);
			}
		}
	}

	public void clear() {
		queue.clear();
	}

	public boolean isQueued(ScheduledHandle handle) {
		return handle != null && queue.contains(handle.target);
	}

	public void cancel(ScheduledHandle handle) {
		if (handle != null) {
			queue.remove(handle.target);
		}
	}

	/** Handle used to track/cancel repeating actions. */
	public static final class ScheduledHandle {
		final Scheduled target;

		private ScheduledHandle(Scheduled target) {
			this.target = target;
		}
	}

	// ------------------------------------------------------------------
	// Humanization helpers
	// ------------------------------------------------------------------

	/** Uniform random long in [min, max]. */
	public static long randomDelay(int min, int max) {
		if (max <= min) {
			return Math.max(0, min);
		}
		return min + (long) (RANDOM.nextDouble() * (max - min + 1));
	}

	/** Adds ±jitterPercent randomness to a base delay. */
	public static long jitter(long baseMs, int jitterPercent) {
		if (jitterPercent <= 0) {
			return baseMs;
		}
		double factor = 1.0 + (RANDOM.nextDouble() * 2 - 1) * (jitterPercent / 100.0);
		return Math.max(0, (long) (baseMs * factor));
	}
}
