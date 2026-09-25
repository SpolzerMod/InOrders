package me.spolzer.inorders.api;

import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

/**
 * A snapshot of an order. {@code delivered} counts items handed in by sellers,
 * {@code collected} counts items the owner has already taken out.
 * {@code priceCents} is the price of one item in hundredths, for whole-unit currencies too: 5 points are 500.
 */
public record Order(long id, UUID owner, String ownerName, ItemStack item, int amount, int delivered, int collected,
		long priceCents, String currency, long created, long expires, OrderStatus status, boolean refunded) {

	@Override
	public ItemStack item() {
		return item.clone();
	}

	/** The price of one item, 2.50 for 250 hundredths. */
	public BigDecimal price() {
		return BigDecimal.valueOf(priceCents, 2);
	}

	public int remaining() {
		return amount - delivered;
	}

	public int uncollected() {
		return delivered - collected;
	}

	/** Active orders past their end time count as expired even before the database is updated. */
	public OrderStatus status(long now) {
		return status == OrderStatus.ACTIVE && expires <= now ? OrderStatus.EXPIRED : status;
	}

	public boolean isOpen(long now) {
		return status(now) == OrderStatus.ACTIVE;
	}
}
