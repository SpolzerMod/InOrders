package me.spolzer.inorders.order;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** One money or item operation per player at a time, so a double click cannot run it twice. */
final class PlayerLocks {
	private final Set<UUID> held = ConcurrentHashMap.newKeySet();

	boolean isHeld(UUID player) {
		return held.contains(player);
	}

	/**
	 * Starts the operation, or returns null if the player already has one running.
	 * The lock is released however the operation ends, even if it throws before returning a future.
	 */
	<T> CompletableFuture<T> tryRun(UUID player, Supplier<CompletableFuture<T>> operation) {
		if (!held.add(player)) return null;
		CompletableFuture<T> started;
		try {
			started = operation.get();
		} catch (RuntimeException e) {
			started = CompletableFuture.failedFuture(e);
		}
		return started.whenComplete((result, error) -> held.remove(player));
	}
}
