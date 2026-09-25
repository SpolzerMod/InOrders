package me.spolzer.inorders.storage;

import me.spolzer.inorders.api.Order;

public record Claim(Order order, int items, int refundItems) {

	public boolean isEmpty() {
		return items == 0 && refundItems == 0;
	}
}
