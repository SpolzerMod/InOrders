package me.spolzer.inorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class OrderDialogs {
	private final InOrdersPlugin plugin;

	public OrderDialogs(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	/** The owner's view of an order, or the staff view. Sellers go straight to {@link #deliver}. */
	public void order(Player player, Order order, Runnable back) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		long now = System.currentTimeMillis();
		boolean own = order.owner().equals(player.getUniqueId());
		if (!own && !player.hasPermission(Permissions.ADMIN_CANCEL)) {
			deliver(player, order, back);
			return;
		}
		Arg[] args = plugin.orderLines().args(player, order, now);
		List<Component> text = new ArrayList<>(messages.lore(player, own ? "order.dialog.owner" : "order.dialog.staff", args));
		if (own && order.isOpen(now)) text.addAll(messages.lore(player, "order.dialog.open", args));
		if (own && order.uncollected() > 0) text.addAll(messages.lore(player, "order.dialog.items", args));
		if (own && !order.isOpen(now) && !order.refunded() && order.remaining() > 0) text.addAll(messages.lore(player, "order.dialog.refund", args));

		List<ActionButton> actions = new ArrayList<>();
		if (own && OrderManager.claimable(order, now)) {
			actions.add(dialogs.button(messages.get(player, "order.claim.name"), () -> plugin.orders().claim(player, order, back)));
		}
		if (order.isOpen(now) && Permissions.canCancel(player, order)) {
			actions.add(dialogs.button(messages.get(player, own ? "order.cancel.name" : "order.remove.name"),
					() -> plugin.screens().confirmCancel(player, order, back)));
		}
		ActionButton close = dialogs.button(messages.get(player, "dialog.back"), back);
		DialogType type = switch (actions.size()) {
			case 0 -> DialogType.notice(close);
			case 1 -> DialogType.confirmation(actions.getFirst(), close);
			default -> DialogType.multiAction(actions, close, 2);
		};
		dialogs.show(player, messages.get(player, "order.title", Arg.of("id", order.id())), Dialogs.body(shown(order), text), List.of(), type);
	}

	public void deliver(Player player, Order order, Runnable back) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		long now = System.currentTimeMillis();
		int have = Items.count(player, order.item());
		int possible = Math.min(have, order.remaining());
		Arg[] args = plugin.orderLines().args(player, order, now);
		ActionButton close = dialogs.button(messages.get(player, "dialog.back"), back);
		Component title = messages.get(player, "deliver.title", args);

		if (possible <= 0) {
			List<Component> text = new ArrayList<>(messages.lore(player, "deliver.dialog", args));
			text.addAll(messages.lore(player, "deliver.none-lore"));
			dialogs.show(player, title, Dialogs.body(shown(order), text), List.of(), DialogType.notice(close));
			return;
		}

		List<DialogInput> inputs = new ArrayList<>();
		if (possible > 1) {
			inputs.add(DialogInput.numberRange("count", messages.get(player, "deliver.slider"), 1, possible)
					.step(1f)
					.initial((float) possible)
					.width(Dialogs.WIDTH)
					.build());
		}
		Arg money = plugin.formats().money(player, "money", order.currency(), plugin.orders().payout(player, order, possible));
		ActionButton deliver = dialogs.button(messages.get(player, "deliver.chosen"), messages.get(player, "deliver.chosen-tooltip", money), view -> {
			Float value = view.getFloat("count");
			int count = value == null ? possible : Math.round(value);
			plugin.orders().deliver(player, order, count, accepted -> back.run());
		});
		dialogs.show(player, title, Dialogs.body(shown(order), messages.lore(player, "deliver.dialog", args)), inputs,
				DialogType.confirmation(deliver, close));
	}

	public void confirm(Player player, Component title, ItemStack subject, List<Component> lines,
			Component yes, Runnable onYes, Component no, Runnable onNo) {
		Dialogs dialogs = plugin.dialogs();
		dialogs.show(player, title, Dialogs.body(subject, lines), List.of(),
				DialogType.confirmation(dialogs.button(yes, onYes), dialogs.button(no, onNo)));
	}

	private static ItemStack shown(Order order) {
		ItemStack item = order.item();
		item.setAmount(Math.clamp(order.remaining(), 1, item.getMaxStackSize()));
		return item;
	}
}
