package me.spolzer.inorders.menu;

import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.text.Arg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class OrderMenu extends Menu {
	private static final int LEFT = 11;
	private static final int ITEM = 13;
	private static final int RIGHT = 15;
	private static final int BACK = 22;

	private final Order order;
	private final Runnable back;

	public OrderMenu(InOrdersPlugin plugin, Player viewer, Order order, Runnable back) {
		super(plugin, viewer, 3, "order.title", Arg.of("id", order.id()));
		this.order = order;
		this.back = back;
	}

	public void show() {
		long now = System.currentTimeMillis();
		boolean own = order.owner().equals(viewer.getUniqueId());
		int have = own ? 0 : Items.count(viewer, order.item());
		List<Component> lines = own ? plugin.orderLines().owner(viewer, order, now) : plugin.orderLines().market(viewer, order, have, now);
		set(ITEM, Icons.describe(order.item(), Math.max(1, order.remaining()), lines));

		if (own) {
			if (OrderManager.claimable(order, now)) {
				set(LEFT, Icons.icon(Material.CHEST_MINECART, messages.item(viewer, "order.claim.name"), messages.lore(viewer, "order.claim.lore")),
						click -> plugin.orders().claim(viewer, order, () -> later(back)));
			}
			if (order.isOpen(now) && viewer.hasPermission(Permissions.CANCEL)) {
				set(RIGHT, Icons.icon(Material.BARRIER, messages.item(viewer, "order.cancel.name"), messages.lore(viewer, "order.cancel.lore")),
						click -> later(() -> plugin.screens().confirmCancel(viewer, order, back)));
			}
		} else if (order.isOpen(now)) {
			set(LEFT, Icons.icon(Material.HOPPER, messages.item(viewer, "order.deliver.name"), messages.lore(viewer, "order.deliver.lore")),
					click -> later(() -> plugin.screens().deliver(viewer, order, back)));
			if (viewer.hasPermission(Permissions.ADMIN_CANCEL)) {
				set(RIGHT, Icons.icon(Material.BARRIER, messages.item(viewer, "order.remove.name"), messages.lore(viewer, "order.remove.lore")),
						click -> later(() -> plugin.screens().confirmCancel(viewer, order, back)));
			}
		}
		set(BACK, Icons.icon(Material.ARROW, messages.item(viewer, "order.back")), click -> later(back));
		open();
	}
}
