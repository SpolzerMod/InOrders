package me.spolzer.inorders.storage;

import org.bukkit.inventory.ItemStack;

/** Item bytes in the database. Separate from the storage because Paper's serializer needs a running server. */
public interface ItemCodec {
	ItemCodec PAPER = new ItemCodec() {
		@Override public byte[] encode(ItemStack item) { return item.serializeAsBytes(); }
		@Override public ItemStack decode(byte[] bytes) { return ItemStack.deserializeBytes(bytes); }
	};

	byte[] encode(ItemStack item);
	ItemStack decode(byte[] bytes);
}
