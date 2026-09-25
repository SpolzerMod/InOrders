package me.spolzer.inorders;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.spolzer.inorders.api.InOrders;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.currency.Currency;
import org.bukkit.entity.Player;

final class OrderService implements InOrders {
	private final InOrdersPlugin plugin;

	OrderService(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public CompletableFuture<Optional<Order>> order(long id) {
		return plugin.storage().order(id);
	}

	@Override
	public CompletableFuture<List<Order>> orders(UUID owner) {
		return plugin.storage().ordersOf(owner);
	}

	@Override
	public void openMenu(Player player) {
		plugin.screens().browse(player);
	}

	@Override
	public Set<String> currencies() {
		Set<String> ids = new LinkedHashSet<>();
		for (Currency currency : plugin.currencies().active()) ids.add(currency.id());
		return ids;
	}
}
