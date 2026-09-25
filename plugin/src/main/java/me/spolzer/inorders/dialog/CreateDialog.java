package me.spolzer.inorders.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.currency.Currency;
import me.spolzer.inorders.currency.Money;
import me.spolzer.inorders.order.Draft;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Formats;
import me.spolzer.inorders.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CreateDialog {
	private final InOrdersPlugin plugin;

	public CreateDialog(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	public void show(Player player, Draft draft, Component error) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		ItemStack item = draft.item();
		List<Currency> allowed = plugin.currencies().allowed(player);

		List<String> balances = new ArrayList<>();
		for (Currency currency : allowed) {
			balances.add(messages.markup(player, "create.dialog.balance-entry",
					plugin.formats().money(player, "balance", currency.id(), currency.balance(player))));
		}
		List<Component> text = new ArrayList<>(messages.lore(player, "create.dialog.text", Formats.item(item),
				Arg.markup("balances", String.join(", ", balances))));
		if (error != null) text.add(error);

		List<DialogInput> inputs = new ArrayList<>();
		inputs.add(DialogInput.text("amount", messages.get(player, "create.dialog.amount"))
				.initial(Integer.toString(draft.amount()))
				.maxLength(16)
				.width(Dialogs.WIDTH)
				.build());
		inputs.add(DialogInput.text("price", messages.get(player, "create.dialog.price"))
				.initial(draft.hasPrice() ? Money.plain(draft.price()) : "")
				.maxLength(16)
				.width(Dialogs.WIDTH)
				.build());
		if (allowed.size() > 1) {
			List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
			for (Currency currency : allowed) {
				options.add(SingleOptionDialogInput.OptionEntry.create(currency.id(),
						messages.get(player, "currency." + currency.id()), currency.id().equals(draft.currency())));
			}
			inputs.add(DialogInput.singleOption("currency", messages.get(player, "create.dialog.currency"), options)
					.width(Dialogs.WIDTH).build());
		}
		List<Integer> durations = plugin.settings().durations;
		if (durations.size() > 1) {
			List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
			for (int days : durations) {
				options.add(SingleOptionDialogInput.OptionEntry.create(Integer.toString(days),
						messages.get(player, "create.menu.days", plugin.formats().days(player, days)), days == draft.days()));
			}
			inputs.add(DialogInput.singleOption("days", messages.get(player, "create.dialog.duration"), options)
					.width(Dialogs.WIDTH).build());
		}

		dialogs.show(player, messages.get(player, "create.title"), Dialogs.body(item, text), inputs,
				DialogType.confirmation(
						dialogs.button(messages.get(player, "create.dialog.next"), null, view -> submit(player, draft, view)),
						dialogs.button(messages.get(player, "create.dialog.back"), () -> plugin.screens().resumePicker(player))));
	}

	private void submit(Player player, Draft draft, DialogResponseView view) {
		Messages messages = plugin.messages();
		String currencyId = view.getText("currency");
		if (currencyId != null && plugin.currencies().byId(currencyId) != null) draft.currency(currencyId);
		String days = view.getText("days");
		if (days != null) draft.days(Integer.parseInt(days));

		String amountText = view.getText("amount");
		int amount = amountText == null ? -1 : Draft.parseAmount(amountText, draft.item().getMaxStackSize());
		if (amount <= 0) {
			show(player, draft, messages.get(player, "create.bad-number", Arg.of("input", amountText == null ? "" : amountText)));
			return;
		}
		draft.amount(amount);
		String priceText = view.getText("price");
		Currency currency = plugin.currencies().byId(draft.currency());
		long price = priceText == null || priceText.isBlank() ? -1 : Money.parse(priceText, currency == null || currency.fractional());
		if (priceText != null && !priceText.isBlank() && price <= 0) {
			show(player, draft, messages.get(player, "create.bad-number", Arg.of("input", priceText)));
			return;
		}
		draft.price(price);
		OrderManager.Problem problem = plugin.orders().check(player, draft);
		if (problem != null) {
			show(player, draft, messages.get(player, problem.key(), problem.args()));
			return;
		}
		confirm(player, draft);
	}

	private void confirm(Player player, Draft draft) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		Formats formats = plugin.formats();
		ItemStack item = draft.item();
		Arg[] args = {
				Formats.item(item),
				formats.amount(player, "amount", draft.amount(), item),
				formats.money(player, "price", draft.currency(), draft.price()),
				formats.money(player, "cost", draft.currency(), plugin.orders().cost(player, draft)),
				formats.money(player, "fee", draft.currency(), plugin.orders().fee(player, draft)),
				formats.days(player, draft.days())
		};
		List<Component> text = new ArrayList<>(messages.lore(player, "create.dialog.summary", args));
		if (plugin.orders().fee(player, draft) > 0) text.addAll(messages.lore(player, "create.dialog.summary-fee", args));
		ItemStack shown = item.clone();
		shown.setAmount(Math.clamp(draft.amount(), 1, item.getMaxStackSize()));

		List<DialogBody> body = Dialogs.body(shown, text);
		dialogs.show(player, messages.get(player, "create.dialog.confirm-title"), body, List.of(),
				DialogType.confirmation(
						dialogs.button(messages.get(player, "create.dialog.create"), messages.get(player, "create.dialog.create-tooltip", args),
								view -> plugin.orders().create(player, draft, created -> {
									if (created) plugin.screens().myOrders(player);
									else show(player, draft, null);
								})),
						dialogs.button(messages.get(player, "create.dialog.edit"), () -> show(player, draft, null))));
	}
}
