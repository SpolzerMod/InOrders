package me.spolzer.inorders.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.catalog.ItemLabels;
import me.spolzer.inorders.currency.Currency;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.storage.OrderQuery;
import me.spolzer.inorders.storage.Sort;
import me.spolzer.inorders.text.Arg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class BrowseMenu extends Menu {
	private static final int PAGE_SIZE = 45;
	private static final int PREVIOUS = 45;
	private static final int SORT = 46;
	private static final int CURRENCY = 47;
	private static final int SEARCH = 48;
	private static final int CREATE = 49;
	private static final int MINE = 50;
	private static final int HELP = 52;
	private static final int NEXT = 53;

	public record State(OrderQuery query, int page) {
		public static State first() { return new State(OrderQuery.defaults(), 0); }
	}

	private State state;
	private boolean loading;
	private boolean shown;

	public BrowseMenu(InOrdersPlugin plugin, Player viewer, State state) {
		super(plugin, viewer, 6, "browse.title");
		this.state = state;
	}

	public State state() { return state; }

	public void show() {
		load(state);
	}

	private void load(State next) {
		if (loading) return;
		loading = true;
		long now = System.currentTimeMillis();
		plugin.storage().browse(next.query(), now, next.page() * PAGE_SIZE, PAGE_SIZE + 1)
				.thenCombine(plugin.storage().ordersOf(viewer.getUniqueId()), Page::new)
				.whenComplete((page, error) -> later(() -> {
					loading = false;
					if (error != null) {
						messages.send(viewer, "error.database");
						return;
					}
					if (page.orders().isEmpty() && next.page() > 0) {
						load(new State(next.query(), 0));
						return;
					}
					if (shown && !isOpen()) return;
					state = next;
					render(page.orders(), page.mine(), System.currentTimeMillis());
					if (!shown) {
						shown = true;
						open();
					}
				}));
	}

	private record Page(List<Order> orders, List<Order> mine) {}

	private void render(List<Order> orders, List<Order> mine, long now) {
		clear();
		OrderLines lines = plugin.orderLines();
		for (int i = 0; i < orders.size() && i < PAGE_SIZE; i++) {
			Order order = orders.get(i);
			boolean own = order.owner().equals(viewer.getUniqueId());
			int have = own ? 0 : Items.count(viewer, order.item());
			List<Component> lore = new ArrayList<>(lines.market(viewer, order, have, now));
			lore.addAll(messages.lore(viewer, own ? "browse.order.own-actions" : "browse.order.actions"));
			if (!own && viewer.hasPermission(Permissions.ADMIN_CANCEL)) lore.addAll(messages.lore(viewer, "browse.order.staff-actions"));
			ItemStack icon = Icons.glowing(Icons.describe(order.item(), order.remaining(), lore), have > 0);
			set(i, icon, click -> clickOrder(order, own, click));
		}
		if (orders.isEmpty()) {
			boolean filtered = state.query().filtered();
			set(22, Icons.icon(Material.PAPER, messages.item(viewer, filtered ? "browse.empty.filtered" : "browse.empty.name"),
					messages.lore(viewer, filtered ? "browse.empty.filtered-lore" : "browse.empty.lore")));
		}

		fill(45, 53, Material.BLACK_STAINED_GLASS_PANE);
		Arg page = Arg.of("page", state.page() + 1);
		if (state.page() > 0) {
			set(PREVIOUS, Icons.icon(Material.ARROW, messages.item(viewer, "browse.previous", page)),
					click -> load(new State(state.query(), state.page() - 1)));
		}
		if (orders.size() > PAGE_SIZE) {
			set(NEXT, Icons.icon(Material.ARROW, messages.item(viewer, "browse.next", page)),
					click -> load(new State(state.query(), state.page() + 1)));
		}
		renderSort();
		renderCurrency();
		renderSearch();
		set(CREATE, Icons.icon(Material.WRITABLE_BOOK, messages.item(viewer, "browse.create.name"), messages.lore(viewer, "browse.create.lore")),
				click -> later(() -> plugin.screens().picker(viewer)));
		renderMine(mine, now);
		set(HELP, Icons.icon(Material.KNOWLEDGE_BOOK, messages.item(viewer, "browse.help.name"), messages.lore(viewer, "browse.help.lore")));
	}

	private void clickOrder(Order order, boolean own, ClickType click) {
		Runnable back = () -> plugin.screens().browse(viewer, state);
		if (own) {
			later(() -> plugin.screens().order(viewer, order, back));
		} else if (click == ClickType.SHIFT_RIGHT && viewer.hasPermission(Permissions.ADMIN_CANCEL)) {
			later(() -> plugin.screens().confirmCancel(viewer, order, back));
		} else if (click.isShiftClick() && click.isLeftClick()) {
			plugin.orders().deliver(viewer, order, Integer.MAX_VALUE, accepted -> load(state));
		} else {
			later(() -> plugin.screens().deliver(viewer, order, back));
		}
	}

	private void renderSort() {
		List<Component> lore = new ArrayList<>();
		for (Sort sort : Sort.values()) {
			Arg name = Arg.markup("name", messages.markup(viewer, "sort." + sort.name().toLowerCase(Locale.ROOT)));
			lore.add(messages.item(viewer, sort == state.query().sort() ? "browse.option.active" : "browse.option.inactive", name));
		}
		lore.addAll(messages.lore(viewer, "browse.option.hint"));
		set(SORT, Icons.icon(Material.HOPPER, messages.item(viewer, "browse.sort"), lore), click -> {
			Sort sort = state.query().sort();
			load(new State(state.query().withSort(click.isRightClick() ? sort.previous() : sort.next()), 0));
		});
	}

	private void renderCurrency() {
		List<Currency> currencies = plugin.currencies().active();
		if (currencies.size() < 2) return;
		List<String> options = new ArrayList<>();
		options.add(null);
		currencies.forEach(currency -> options.add(currency.id()));
		String current = state.query().currency();
		List<Component> lore = new ArrayList<>();
		for (String option : options) {
			Arg name = option == null ? Arg.markup("name", messages.markup(viewer, "browse.currency-all")) : Arg.markup("name", messages.markup(viewer, "currency." + option));
			lore.add(messages.item(viewer, Objects.equals(option, current) ? "browse.option.active" : "browse.option.inactive", name));
		}
		lore.addAll(messages.lore(viewer, "browse.option.hint"));
		Currency selected = current == null ? null : plugin.currencies().byId(current);
		Material icon = selected == null ? Material.SUNFLOWER : selected.icon();
		set(CURRENCY, Icons.icon(icon, messages.item(viewer, "browse.currency"), lore), click -> {
			int index = options.indexOf(current);
			int step = click.isRightClick() ? options.size() - 1 : 1;
			load(new State(state.query().withCurrency(options.get((index + step) % options.size())), 0));
		});
	}

	private void renderSearch() {
		OrderQuery query = state.query();
		boolean active = query.search() != null || query.item() != null;
		Arg value = query.item() != null ? Arg.of("query", ItemLabels.name(query.item())) : Arg.of("query", query.search() == null ? "" : query.search());
		ItemStack icon = active
				? Icons.glowing(Icons.icon(Material.SPYGLASS, messages.item(viewer, "browse.search.active", value),
						messages.lore(viewer, "browse.search.active-lore")), true)
				: Icons.icon(Material.SPYGLASS, messages.item(viewer, "browse.search.name"), messages.lore(viewer, "browse.search.lore"));
		set(SEARCH, icon, click -> {
			if (click.isRightClick() && active) {
				load(new State(query.withSearch(null), 0));
				return;
			}
			if (plugin.clients().usesDialogs(viewer, plugin.settings())) {
				later(() -> plugin.screens().findOrders(viewer, state));
				return;
			}
			String current = query.search();
			later(() -> plugin.input().ask(viewer, "browse.search.input", current == null ? "" : current, text -> {
				String typed = text == null || text.isBlank() ? current : text.trim().toLowerCase(Locale.ROOT);
				plugin.screens().browse(viewer, new State(query.withSearch(typed), 0));
			}));
		});
	}

	private void renderMine(List<Order> mine, long now) {
		long open = mine.stream().filter(order -> order.isOpen(now)).count();
		long ready = mine.stream().filter(order -> OrderManager.claimable(order, now)).count();
		List<Component> lore = new ArrayList<>(messages.lore(viewer, "browse.mine.lore",
				Arg.of("open", open), plugin.formats().limit(viewer, plugin.orders().limit(viewer))));
		if (ready > 0) lore.addAll(messages.lore(viewer, "browse.mine.ready", Arg.of("count", ready)));
		set(MINE, Icons.glowing(Icons.head(viewer.getUniqueId(), messages.item(viewer, "browse.mine.name"), lore), ready > 0),
				click -> later(() -> plugin.screens().myOrders(viewer)));
	}
}
