package me.spolzer.inorders.api.event;

import java.math.BigDecimal;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Called before the payment for a new order is taken. */
public final class OrderCreateEvent extends Event implements Cancellable {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Player player;
	private final ItemStack item;
	private final int amount;
	private final long priceCents;
	private final String currency;
	private boolean cancelled;

	public OrderCreateEvent(Player player, ItemStack item, int amount, long priceCents, String currency) {
		this.player = player;
		this.item = item;
		this.amount = amount;
		this.priceCents = priceCents;
		this.currency = currency;
	}

	public Player player() { return player; }
	public ItemStack item() { return item.clone(); }
	public int amount() { return amount; }
	/** Hundredths, see {@link me.spolzer.inorders.api.Order}. */
	public long priceCents() { return priceCents; }
	public BigDecimal price() { return BigDecimal.valueOf(priceCents, 2); }
	public String currency() { return currency; }

	@Override public boolean isCancelled() { return cancelled; }
	@Override public void setCancelled(boolean cancel) { cancelled = cancel; }

	@Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
	public static HandlerList getHandlerList() { return HANDLERS; }
}
