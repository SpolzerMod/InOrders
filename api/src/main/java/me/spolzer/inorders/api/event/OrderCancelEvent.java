package me.spolzer.inorders.api.event;

import me.spolzer.inorders.api.Order;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Called when the owner or a staff member cancels an order. Expired orders do not fire it. */
public final class OrderCancelEvent extends Event implements Cancellable {
	private static final HandlerList HANDLERS = new HandlerList();

	private final CommandSender by;
	private final Order order;
	private boolean cancelled;

	public OrderCancelEvent(CommandSender by, Order order) {
		this.by = by;
		this.order = order;
	}

	public CommandSender by() { return by; }
	public Order order() { return order; }

	@Override public boolean isCancelled() { return cancelled; }
	@Override public void setCancelled(boolean cancel) { cancelled = cancel; }

	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
