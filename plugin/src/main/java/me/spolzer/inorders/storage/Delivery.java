package me.spolzer.inorders.storage;

public record Delivery(int accepted, boolean filled) {

	static final Delivery NONE = new Delivery(0, false);
}
