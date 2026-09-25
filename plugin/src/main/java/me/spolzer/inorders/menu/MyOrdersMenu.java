package me.spolzer.inorders.menu;

import java.util.ArrayList;
import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.text.Arg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public final class MyOrdersMenu extends Menu {
	private static final int PAGE_SIZE = 45;
	private static final int BACK = 45;
	private static final int CLAIM_ALL = 47;
	private static final int CREATE = 49;
	private static final int PREVIOUS = 52;
	private static final int NEXT = 53;

	private int page;
	private boolean shown;

	public MyOrdersMenu(InOrdersPlugin plugin, Player viewer) {
		super(plugin, viewer, 6, "mine.title");
	}

	public void show() {
		plugin.storage().ordersOf(viewer.getUniqueId()).whenComplete((orders, error) -> later(() -> {
			if (error != null) {
				messages.send(viewer, "error.database");
				return;
			}
			if (shown && !isOpen()) return;
			render(orders, System.currentTimeMillis());
			if (!shown) {
				shown = true;
				open();
			}
		}));
	}

	private void render(List<Order> orders, long now) {
		clear();
		int pages = Math.max(1, (orders.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);
		OrderLines lines = plugin.orderLines();
		boolean anyReady = false;
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= orders.size()) break;
			Order order = orders.get(index);
			boolean ready = OrderManager.claimable(order, now);
			anyReady |= ready;
			List<Component> lore = new ArrayList<>(lines.owner(viewer, order, now));
			lore.add(Component.empty());
			if (ready) lore.addAll(messages.lore(viewer, "mine.actions.claim"));
			lore.addAll(messages.lore(viewer, "mine.actions.details"));
			if (order.isOpen(now)) lore.addAll(messages.lore(viewer, "mine.actions.cancel"));
			set(i, Icons.glowing(Icons.describe(order.item(), Math.max(1, order.remaining()), lore), ready),
					click -> clickOrder(order, ready, click, now));
		}
		if (orders.isEmpty()) {
			set(22, Icons.icon(Material.PAPER, messages.item(viewer, "mine.empty.name"), messages.lore(viewer, "mine.empty.lore")));
		}

		fill(45, 53, Material.BLACK_STAINED_GLASS_PANE);
		set(BACK, Icons.icon(Material.ARROW, messages.item(viewer, "mine.back")), click -> later(() -> plugin.screens().browse(viewer)));
		set(CLAIM_ALL, Icons.glowing(Icons.icon(Material.CHEST_MINECART, messages.item(viewer, "mine.claim-all.name"),
				messages.lore(viewer, anyReady ? "mine.claim-all.lore" : "mine.claim-all.nothing")), anyReady), click -> {
			if (!orders.isEmpty()) plugin.orders().claimAll(viewer, this::show);
		});
		set(CREATE, Icons.icon(Material.WRITABLE_BOOK, messages.item(viewer, "browse.create.name"), messages.lore(viewer, "browse.create.lore")),
				click -> later(() -> plugin.screens().picker(viewer)));
		if (page > 0) {
			set(PREVIOUS, Icons.icon(Material.ARROW, messages.item(viewer, "browse.previous", Arg.of("page", page + 1))), click -> {
				page--;
				render(orders, System.currentTimeMillis());
			});
		}
		if (page < pages - 1) {
			set(NEXT, Icons.icon(Material.ARROW, messages.item(viewer, "browse.next", Arg.of("page", page + 1))), click -> {
				page++;
				render(orders, System.currentTimeMillis());
			});
		}
	}

	private void clickOrder(Order order, boolean ready, ClickType click, long now) {
		if (click == ClickType.SHIFT_RIGHT && order.isOpen(now)) {
			later(() -> plugin.screens().confirmCancel(viewer, order, () -> plugin.screens().myOrders(viewer)));
		} else if (click.isLeftClick() && ready) {
			plugin.orders().claim(viewer, order, this::show);
		} else {
			later(() -> plugin.screens().order(viewer, order, () -> plugin.screens().myOrders(viewer)));
		}
	}
}
