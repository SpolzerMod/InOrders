package me.spolzer.inorders.menu;

import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.currency.Currency;
import me.spolzer.inorders.dialog.CatalogDialog;
import me.spolzer.inorders.dialog.CreateDialog;
import me.spolzer.inorders.dialog.OrderDialogs;
import me.spolzer.inorders.order.Draft;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Screens {
	private final InOrdersPlugin plugin;
	private final CatalogDialog catalogDialog;
	private final CreateDialog createDialog;
	private final OrderDialogs orderDialogs;

	public Screens(InOrdersPlugin plugin) {
		this.plugin = plugin;
		this.catalogDialog = new CatalogDialog(plugin);
		this.createDialog = new CreateDialog(plugin);
		this.orderDialogs = new OrderDialogs(plugin);
	}

	private boolean dialogs(Player player) {
		return plugin.clients().usesDialogs(player, plugin.settings());
	}

	private boolean allowed(Player player, String permission) {
		if (!player.hasPermission(permission)) {
			plugin.messages().send(player, "error.no-permission");
			return false;
		}
		if (plugin.settings().isWorldDisabled(player.getWorld().getName()) && !player.hasPermission(Permissions.ADMIN_WORLDS)) {
			plugin.messages().send(player, "error.disabled-world");
			return false;
		}
		return true;
	}

	public void browse(Player player) {
		browse(player, BrowseMenu.State.first());
	}

	public void browse(Player player, BrowseMenu.State state) {
		if (allowed(player, Permissions.USE)) new BrowseMenu(plugin, player, state).show();
	}

	public void myOrders(Player player) {
		if (allowed(player, Permissions.USE)) new MyOrdersMenu(plugin, player).show();
	}

	public void picker(Player player) {
		if (!allowed(player, Permissions.CREATE)) return;
		if (plugin.currencies().allowed(player).isEmpty()) {
			plugin.messages().send(player, "create.no-currency");
			return;
		}
		if (dialogs(player)) catalogDialog.create(player);
		else new PickerMenu(plugin, player).show();
	}

	/** Back from the order form to the catalog page the item was picked on. */
	public void resumePicker(Player player) {
		if (dialogs(player)) catalogDialog.resume(player);
		else picker(player);
	}

	public void findOrders(Player player, BrowseMenu.State back) {
		if (dialogs(player)) catalogDialog.find(player, back);
	}

	public void forget(Player player) {
		catalogDialog.forget(player.getUniqueId());
	}

	public Draft draft(Player player, ItemStack item) {
		// The first currency the player actually has, so the form does not open on an empty balance
		List<Currency> currencies = plugin.currencies().allowed(player);
		String currency = currencies.stream().filter(option -> option.balance(player) > 0).findFirst()
				.or(() -> currencies.stream().findFirst()).map(Currency::id).orElse("money");
		return new Draft(item, currency, plugin.settings().defaultDuration);
	}

	public void create(Player player, Draft draft) {
		if (!allowed(player, Permissions.CREATE)) return;
		if (dialogs(player)) createDialog.show(player, draft, null);
		else new CreateMenu(plugin, player, draft).show();
	}

	public void order(Player player, Order order, Runnable back) {
		if (dialogs(player)) orderDialogs.order(player, order, back);
		else new OrderMenu(plugin, player, order, back).show();
	}

	public void deliver(Player player, Order order, Runnable back) {
		if (!allowed(player, Permissions.DELIVER)) return;
		if (dialogs(player)) orderDialogs.deliver(player, order, back);
		else new DeliverMenu(plugin, player, order, back).show();
	}

	public void confirmCancel(Player player, Order order, Runnable back) {
		Messages messages = plugin.messages();
		if (!Permissions.canCancel(player, order)) {
			messages.send(player, "error.no-permission");
			return;
		}
		boolean own = order.owner().equals(player.getUniqueId());
		Arg[] args = plugin.orderLines().args(player, order, System.currentTimeMillis());
		List<Component> lines = messages.lore(player, own ? "cancel.confirm.own" : "cancel.confirm.staff", args);
		Component yes = messages.get(player, "cancel.confirm.accept");
		Component no = messages.get(player, "cancel.confirm.keep");
		Runnable onYes = () -> plugin.orders().cancel(player, order, back);
		if (dialogs(player)) {
			orderDialogs.confirm(player, messages.get(player, "cancel.confirm.title", args), order.item(), lines, yes, onYes, no, back);
		} else {
			new ConfirmMenu(plugin, player, "cancel.confirm.title", args, order.item(), lines.subList(1, lines.size()),
					messages.item(player, "cancel.confirm.accept"), onYes, messages.item(player, "cancel.confirm.keep"), back).open();
		}
	}
}
