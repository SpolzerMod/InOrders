package me.spolzer.inorders.listener;

import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.text.Arg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
	private final InOrdersPlugin plugin;

	public PlayerListener(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (!plugin.settings().joinReminder) return;
		Player player = event.getPlayer();
		plugin.storage().ordersOf(player.getUniqueId()).thenAccept(orders -> {
			long now = System.currentTimeMillis();
			long ready = orders.stream().filter(order -> OrderManager.claimable(order, now)).count();
			if (ready == 0) return;
			// A short delay, otherwise the line gets lost among the join messages
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (player.isOnline()) plugin.messages().send(player, "notify.join", Arg.of("count", ready));
			}, 40L);
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		plugin.screens().forget(event.getPlayer());
	}
}
