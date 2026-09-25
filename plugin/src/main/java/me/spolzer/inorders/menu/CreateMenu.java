package me.spolzer.inorders.menu;

import java.util.ArrayList;
import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.currency.Currency;
import me.spolzer.inorders.currency.Money;
import me.spolzer.inorders.order.Draft;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Formats;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class CreateMenu extends Menu {
	private static final int ITEM = 13;
	private static final int[] MINUS = {21, 20, 19};
	private static final int AMOUNT = 22;
	private static final int[] PLUS = {23, 24, 25};
	private static final int PRICE = 29;
	private static final int CURRENCY = 31;
	private static final int DURATION = 33;
	private static final int CHANGE = 45;
	private static final int CONFIRM = 49;
	private static final int CANCEL = 53;

	private final Draft draft;

	public CreateMenu(InOrdersPlugin plugin, Player viewer, Draft draft) {
		super(plugin, viewer, 6, "create.title");
		this.draft = draft;
	}

	public void show() {
		render();
		open();
	}

	private void render() {
		clear();
		fill(45, 53, Material.BLACK_STAINED_GLASS_PANE);
		ItemStack item = draft.item();
		Formats formats = plugin.formats();
		Arg amount = formats.amount(viewer, "amount", draft.amount(), item);

		set(ITEM, Icons.describe(item, draft.amount(), messages.lore(viewer, "create.menu.item", Formats.item(item), amount)));

		int[] steps = steps(item.getMaxStackSize());
		for (int i = 0; i < steps.length; i++) {
			int step = steps[i];
			ItemStack minus = Icons.icon(Material.RED_STAINED_GLASS_PANE, messages.item(viewer, "create.menu.minus", Arg.of("step", step)));
			minus.setAmount(Math.min(step, 64));
			set(MINUS[i], minus, click -> changeAmount(-step));
			ItemStack plus = Icons.icon(Material.LIME_STAINED_GLASS_PANE, messages.item(viewer, "create.menu.plus", Arg.of("step", step)));
			plus.setAmount(Math.min(step, 64));
			set(PLUS[i], plus, click -> changeAmount(step));
		}
		set(AMOUNT, Icons.icon(Material.BUNDLE, messages.item(viewer, "create.menu.amount", amount),
				messages.lore(viewer, "create.menu.amount-lore", Arg.of("max", plugin.settings().maxAmount))), click -> askAmount());

		Currency currency = plugin.currencies().byId(draft.currency());
		String currencyId = currency == null ? "money" : currency.id();
		Component priceName = draft.hasPrice()
				? messages.item(viewer, "create.menu.price", formats.money(viewer, "price", currencyId, draft.price()))
				: messages.item(viewer, "create.menu.price-empty");
		set(PRICE, Icons.glowing(Icons.icon(Material.GOLD_NUGGET, priceName, messages.lore(viewer, "create.menu.price-lore")), !draft.hasPrice()),
				click -> askPrice());

		List<Currency> allowed = plugin.currencies().allowed(viewer);
		if (currency != null) {
			List<Component> lore = new ArrayList<>();
			for (Currency option : allowed) {
				lore.add(messages.item(viewer, option == currency ? "browse.option.active" : "browse.option.inactive",
						Arg.markup("name", messages.markup(viewer, "currency." + option.id()))));
			}
			if (allowed.size() > 1) lore.addAll(messages.lore(viewer, "create.menu.switch"));
			set(CURRENCY, Icons.icon(currency.icon(), messages.item(viewer, "create.menu.currency",
					Arg.markup("name", messages.markup(viewer, "currency." + currency.id()))), lore), click -> {
				if (allowed.size() < 2) return;
				int index = allowed.indexOf(currency);
				int step = click.isRightClick() ? allowed.size() - 1 : 1;
				draft.currency(allowed.get((index + step) % allowed.size()).id()).price(-1);
				render();
			});
		}

		List<Integer> durations = plugin.settings().durations;
		List<Component> durationLore = new ArrayList<>();
		for (int days : durations) {
			durationLore.add(messages.item(viewer, days == draft.days() ? "browse.option.active" : "browse.option.inactive",
					Arg.markup("name", messages.markup(viewer, "create.menu.days", formats.days(viewer, days)))));
		}
		if (durations.size() > 1) durationLore.addAll(messages.lore(viewer, "create.menu.switch"));
		set(DURATION, Icons.icon(Material.CLOCK, messages.item(viewer, "create.menu.duration", formats.days(viewer, draft.days())), durationLore),
				click -> {
					if (durations.size() < 2) return;
					int index = Math.max(0, durations.indexOf(draft.days()));
					int step = click.isRightClick() ? durations.size() - 1 : 1;
					draft.days(durations.get((index + step) % durations.size()));
					render();
				});

		set(CHANGE, Icons.icon(Material.ARROW, messages.item(viewer, "create.menu.change")), click -> later(() -> plugin.screens().picker(viewer)));
		set(CANCEL, Icons.icon(Material.BARRIER, messages.item(viewer, "create.menu.cancel")), click -> later(() -> plugin.screens().browse(viewer)));
		renderConfirm(currencyId);
	}

	private void renderConfirm(String currencyId) {
		OrderManager.Problem problem = plugin.orders().check(viewer, draft);
		if (problem != null) {
			set(CONFIRM, Icons.icon(Material.GRAY_DYE, messages.item(viewer, "create.menu.not-ready"),
					List.of(messages.item(viewer, problem.key(), problem.args()))));
			return;
		}
		Formats formats = plugin.formats();
		Arg cost = formats.money(viewer, "cost", currencyId, plugin.orders().cost(viewer, draft));
		Arg fee = formats.money(viewer, "fee", currencyId, plugin.orders().fee(viewer, draft));
		List<Component> lore = new ArrayList<>(messages.lore(viewer, "create.menu.confirm-lore", cost));
		if (plugin.orders().fee(viewer, draft) > 0) lore.addAll(messages.lore(viewer, "create.dialog.summary-fee", fee));
		set(CONFIRM, Icons.glowing(Icons.icon(Material.EMERALD, messages.item(viewer, "create.menu.confirm"), lore), true),
				click -> plugin.orders().create(viewer, draft, created -> {
					if (created) plugin.screens().myOrders(viewer);
					else if (isOpen()) render();
				}));
	}

	private void changeAmount(int delta) {
		draft.amount(Math.clamp((long) draft.amount() + delta, 1, plugin.settings().maxAmount));
		render();
	}

	private void askAmount() {
		later(() -> plugin.input().ask(viewer, "create.input.amount", Integer.toString(draft.amount()), text -> {
			if (text != null) {
				int amount = Draft.parseAmount(text, draft.item().getMaxStackSize());
				if (amount > 0) draft.amount(Math.min(amount, plugin.settings().maxAmount));
				else messages.send(viewer, "create.bad-number", Arg.of("input", text));
			}
			show();
		}));
	}

	private void askPrice() {
		String initial = draft.hasPrice() ? Money.plain(draft.price()) : "";
		later(() -> plugin.input().ask(viewer, "create.input.price", initial, text -> {
			if (text != null) {
				Currency currency = plugin.currencies().byId(draft.currency());
				long price = Money.parse(text, currency == null || currency.fractional());
				if (price > 0) draft.price(price);
				else messages.send(viewer, "create.bad-number", Arg.of("input", text));
			}
			show();
		}));
	}

	static int[] steps(int stack) {
		if (stack >= 16) return new int[] {1, stack / 4, stack};
		if (stack > 1) return new int[] {1, stack / 2, stack};
		return new int[] {1, 5, 10};
	}
}
