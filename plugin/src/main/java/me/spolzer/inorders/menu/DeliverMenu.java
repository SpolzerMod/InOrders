package me.spolzer.inorders.menu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Formats;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DeliverMenu extends Menu {
	private static final int ITEM = 4;
	private static final int[] AMOUNTS = {10, 11, 12, 13, 14, 15, 16};
	private static final int BACK = 22;

	private final Runnable back;
	private Order order;

	public DeliverMenu(InOrdersPlugin plugin, Player viewer, Order order, Runnable back) {
		super(plugin, viewer, 3, "deliver.title", Arg.of("player", order.ownerName()));
		this.order = order;
		this.back = back;
	}

	public void show() {
		render();
		open();
	}

	private void render() {
		clear();
		long now = System.currentTimeMillis();
		ItemStack template = order.item();
		int have = Items.count(viewer, template);
		int possible = Math.min(have, order.remaining());
		set(ITEM, Icons.describe(template, order.remaining(), plugin.orderLines().market(viewer, order, have, now)));

		List<Integer> options = options(template.getMaxStackSize(), possible);
		int first = (AMOUNTS.length - options.size()) / 2;
		for (int i = 0; i < options.size(); i++) {
			int amount = options.get(i);
			boolean all = i == options.size() - 1 && amount == possible;
			List<Component> lore = messages.lore(viewer, "deliver.button-lore",
					plugin.formats().money(viewer, "money", order.currency(), plugin.orders().payout(viewer, order, amount)));
			ItemStack icon = Icons.named(template,
					messages.item(viewer, all ? "deliver.all" : "deliver.amount", Arg.of("count", amount), Formats.item(template)), lore);
			icon.setAmount(Math.clamp(amount, 1, icon.getMaxStackSize()));
			set(AMOUNTS[first + i], Icons.glowing(icon, all), click -> deliver(amount));
		}
		if (options.isEmpty()) {
			set(13, Icons.icon(Material.BARRIER, messages.item(viewer, "deliver.none", Formats.item(template)), messages.lore(viewer, "deliver.none-lore")));
		}
		set(BACK, Icons.icon(Material.ARROW, messages.item(viewer, "deliver.back")), click -> later(back));
	}

	private static List<Integer> options(int stack, int possible) {
		Set<Integer> amounts = new LinkedHashSet<>();
		for (int amount : new int[] {1, Math.max(1, stack / 4), stack, stack * 4, stack * 9}) {
			if (amount < possible) amounts.add(amount);
		}
		if (possible > 0) amounts.add(possible);
		List<Integer> list = new ArrayList<>(amounts);
		while (list.size() > AMOUNTS.length) list.remove(list.size() - 2);
		return list;
	}

	private void deliver(int amount) {
		plugin.orders().deliver(viewer, order, amount, accepted -> {
			if (accepted <= 0 || !isOpen()) return;
			plugin.storage().order(order.id()).whenComplete((fresh, error) -> later(() -> {
				if (!isOpen()) return;
				if (error != null || fresh.isEmpty() || !fresh.get().isOpen(System.currentTimeMillis())) {
					back.run();
					return;
				}
				order = fresh.get();
				render();
			}));
		});
	}
}
