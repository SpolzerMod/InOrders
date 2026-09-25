package me.spolzer.inorders.storage;

public enum Sort {
	NEWEST("created DESC"),
	// Prices in different currencies cannot be compared, so orders are grouped by currency first
	PRICE("currency, price_cents DESC, created DESC"),
	AMOUNT("(amount - delivered) DESC, created DESC"),
	ENDING("expires ASC");

	final String sql;

	Sort(String sql) {
		this.sql = sql;
	}

	public Sort next() { return values()[(ordinal() + 1) % values().length]; }
	public Sort previous() { return values()[(ordinal() + values().length - 1) % values().length]; }
}
