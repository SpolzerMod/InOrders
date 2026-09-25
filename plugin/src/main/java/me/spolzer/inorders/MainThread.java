package me.spolzer.inorders;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/**
 * Runs database callbacks on the main thread. The scheduler refuses tasks from a disabled plugin,
 * so on shutdown {@link #finish} drains the queue itself and a saved delivery still pays the seller.
 */
public final class MainThread implements Executor {
	private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

	private final Plugin plugin;
	private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

	public MainThread(Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void execute(Runnable task) {
		if (Bukkit.isPrimaryThread()) {
			run(task);
			return;
		}
		queue.add(task);
		if (!plugin.isEnabled()) return;
		try {
			Bukkit.getScheduler().runTask(plugin, this::drain);
		} catch (IllegalPluginAccessException e) {
			// Disabled a moment ago, finish() will run the task
		}
	}

	/** The plugin is being disabled. Callbacks still run, but menus should not be reopened. */
	public boolean stopping() {
		return !plugin.isEnabled();
	}

	/** Runs queued callbacks until {@code working} turns false. Returns false on timeout. */
	public boolean finish(BooleanSupplier working, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		try {
			while (true) {
				drain();
				if (!working.getAsBoolean() && queue.isEmpty()) return true;
				long left = deadline - System.nanoTime();
				if (left <= 0) return false;
				Runnable task = queue.poll(Math.min(left, POLL_NANOS), TimeUnit.NANOSECONDS);
				if (task != null) run(task);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private void drain() {
		Runnable task;
		while ((task = queue.poll()) != null) run(task);
	}

	private void run(Runnable task) {
		try {
			task.run();
		} catch (RuntimeException e) {
			plugin.getLogger().log(Level.SEVERE, "A task on the main thread failed", e);
		}
	}
}
