package me.spolzer.inorders.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public interface InOrders {

	static InOrders get() {
		RegisteredServiceProvider<InOrders> provider = Bukkit.getServicesManager().getRegistration(InOrders.class);
		if (provider == null) throw new IllegalStateException("InOrders is not enabled");
		return provider.getProvider();
	}

	CompletableFuture<Optional<Order>> order(long id);

	/** Orders of the player that are still active or have something left to collect. */
	CompletableFuture<List<Order>> orders(UUID owner);

	/** Opens the order list. Must be called on the main thread. */
	void openMenu(Player player);

	/** Ids of the enabled currencies: money, playerpoints. */
	Set<String> currencies();
}
