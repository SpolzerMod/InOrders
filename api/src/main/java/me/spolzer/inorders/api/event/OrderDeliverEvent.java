package me.spolzer.inorders.api.event;

import me.spolzer.inorders.api.Order;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called before the items are taken from the seller. The amount may still be lowered
 * if another seller fills the order at the same moment.
 */
public final class OrderDeliverEvent extends Event implements Cancellable {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player seller;
	private final Order order;
	private final int amount;
	private boolean cancelled;

	public OrderDeliverEvent(Player seller, Order order, int amount) {
		this.seller = seller;
		this.order = order;
		this.amount = amount;
	}

	public Player seller() { return seller; }
	public Order order() { return order; }
	public int amount() { return amount; }

	@Override public boolean isCancelled() { return cancelled; }
	@Override public void setCancelled(boolean cancel) { cancelled = cancel; }

	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
