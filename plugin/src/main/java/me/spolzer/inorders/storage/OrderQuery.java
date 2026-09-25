package me.spolzer.inorders.storage;

import org.bukkit.inventory.ItemStack;

/**
 * Filters of the order list. {@code search} matches part of the item names, {@code item} picks one exact item:
 * {@code itemKey} is its stored search text, which differs between a Mending and a Sharpness book.
 */
public record OrderQuery(Sort sort, String currency, String search, ItemStack item, String itemKey) {

	public static OrderQuery defaults() {
		return new OrderQuery(Sort.NEWEST, null, null, null, null);
	}

	public boolean filtered() {
		return currency != null || search != null || item != null;
	}

	public OrderQuery withSort(Sort value) { return new OrderQuery(value, currency, search, item, itemKey); }
	public OrderQuery withCurrency(String value) { return new OrderQuery(sort, value, search, item, itemKey); }
	public OrderQuery withSearch(String value) { return new OrderQuery(sort, currency, value, null, null); }
	public OrderQuery withItem(ItemStack value, String key) { return new OrderQuery(sort, currency, null, value, key); }
}
