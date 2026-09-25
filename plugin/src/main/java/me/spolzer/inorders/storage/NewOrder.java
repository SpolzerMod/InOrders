package me.spolzer.inorders.storage;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public record NewOrder(UUID owner, String ownerName, ItemStack item, String search, int amount,
		long price, String currency, long created, long expires) {}
