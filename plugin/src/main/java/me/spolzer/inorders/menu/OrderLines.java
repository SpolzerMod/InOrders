package me.spolzer.inorders.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.api.OrderStatus;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Formats;
import me.spolzer.inorders.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class OrderLines {
	private final InOrdersPlugin plugin;

	public OrderLines(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	public Arg[] args(Player viewer, Order order, long now) {
		Formats formats = plugin.formats();
		ItemStack item = order.item();
		OrderStatus status = order.status(now);
		return new Arg[] {
				Arg.of("id", order.id()),
				Arg.of("player", order.ownerName()),
				Formats.item(item),
				formats.money(viewer, "price", order.currency(), order.priceCents()),
				formats.money(viewer, "payout", order.currency(), plugin.orders().payout(viewer, order, 1)),
				formats.money(viewer, "refund", order.currency(), plugin.orders().refund(order, order.remaining())),
				formats.amount(viewer, "amount", order.amount(), item),
				formats.amount(viewer, "remaining", order.remaining(), item),
				Arg.of("delivered", order.delivered()),
				formats.amount(viewer, "uncollected", order.uncollected(), item),
				formats.progress(viewer, order.delivered(), order.amount()),
				formats.timeLeft(viewer, order.expires() - now),
				Arg.markup("status", plugin.messages().markup(viewer, "order.status." + status.name().toLowerCase(Locale.ROOT)))
		};
	}

	public List<Component> market(Player viewer, Order order, int have, long now) {
		return market(viewer, order, have, now, true);
	}

	public List<Component> market(Player viewer, Order order, int have, long now, boolean header) {
		Messages messages = plugin.messages();
		Arg[] args = args(viewer, order, now);
		List<Component> lines = new ArrayList<>();
		if (header) lines.addAll(messages.lore(viewer, "order.header", args));
		lines.addAll(messages.lore(viewer, "order.market", args));
		if (have > 0) lines.addAll(messages.lore(viewer, "order.have", Arg.of("count", have),
				plugin.formats().money(viewer, "money", order.currency(), plugin.orders().payout(viewer, order, Math.min(have, order.remaining())))));
		return lines;
	}

	public List<Component> owner(Player viewer, Order order, long now) {
		return owner(viewer, order, now, true);
	}

	public List<Component> owner(Player viewer, Order order, long now, boolean header) {
		Messages messages = plugin.messages();
		Arg[] args = args(viewer, order, now);
		List<Component> lines = new ArrayList<>();
		if (header) lines.addAll(messages.lore(viewer, "order.header", args));
		lines.addAll(messages.lore(viewer, "order.owner", args));
		if (order.isOpen(now)) lines.addAll(messages.lore(viewer, "order.owner-open", args));
		if (order.uncollected() > 0) lines.addAll(messages.lore(viewer, "order.owner-items", args));
		if (!order.isOpen(now) && !order.refunded() && order.remaining() > 0) lines.addAll(messages.lore(viewer, "order.owner-refund", args));
		return lines;
	}
}
