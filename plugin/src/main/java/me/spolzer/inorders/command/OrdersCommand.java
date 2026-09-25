package me.spolzer.inorders.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.menu.BrowseMenu;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.storage.OrderQuery;
import me.spolzer.inorders.text.Arg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class OrdersCommand implements TabExecutor {
	private final InOrdersPlugin plugin;

	public OrdersCommand(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
		String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "reload" -> {
				if (!sender.hasPermission(Permissions.ADMIN_RELOAD)) {
					plugin.messages().send(sender, "error.no-permission");
					return true;
				}
				plugin.reload().whenComplete((ok, error) ->
						plugin.messages().send(sender, error == null ? "command.reloaded" : "command.reload-failed"));
			}
			case "open" -> open(sender, args);
			case "my", "create", "search", "" -> {
				if (!(sender instanceof Player player)) {
					plugin.messages().send(sender, "error.players-only");
					return true;
				}
				run(player, sub, args);
			}
			default -> plugin.messages().send(sender, "command.usage", Arg.of("label", label));
		}
		return true;
	}

	private void run(Player player, String sub, String[] args) {
		switch (sub) {
			case "my" -> plugin.screens().myOrders(player);
			case "create" -> {
				ItemStack hand = player.getInventory().getItemInMainHand();
				if (args.length > 1 && args[1].equalsIgnoreCase("hand")) {
					if (hand.isEmpty()) {
						plugin.messages().send(player, "command.empty-hand");
						return;
					}
					ItemStack item = Items.template(hand);
					if (plugin.settings().isBlocked(item.getType())) {
						plugin.messages().send(player, "create.blocked");
						return;
					}
					plugin.screens().create(player, plugin.screens().draft(player, item));
				} else {
					plugin.screens().picker(player);
				}
			}
			case "search" -> {
				String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim().toLowerCase(Locale.ROOT);
				OrderQuery query = OrderQuery.defaults().withSearch(text.isEmpty() ? null : text);
				plugin.screens().browse(player, new BrowseMenu.State(query, 0));
			}
			default -> plugin.screens().browse(player);
		}
	}

	private void open(CommandSender sender, String[] args) {
		if (!sender.hasPermission(Permissions.ADMIN_OPEN)) {
			plugin.messages().send(sender, "error.no-permission");
			return;
		}
		Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
		if (target == null) {
			plugin.messages().send(sender, "command.no-player", Arg.of("player", args.length > 1 ? args[1] : "?"));
			return;
		}
		if (args.length > 2 && args[2].equalsIgnoreCase("my")) plugin.screens().myOrders(target);
		else plugin.screens().browse(target);
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
		List<String> options = new ArrayList<>();
		if (args.length == 1) {
			if (sender.hasPermission(Permissions.USE)) options.addAll(List.of("my", "search"));
			if (sender.hasPermission(Permissions.CREATE)) options.add("create");
			if (sender.hasPermission(Permissions.ADMIN_OPEN)) options.add("open");
			if (sender.hasPermission(Permissions.ADMIN_RELOAD)) options.add("reload");
		} else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
			options.add("hand");
		} else if (args.length == 2 && args[0].equalsIgnoreCase("open") && sender.hasPermission(Permissions.ADMIN_OPEN)) {
			Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
		} else if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
			options.add("my");
		}
		String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
		return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
	}
}
