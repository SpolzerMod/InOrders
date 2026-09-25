package me.spolzer.inorders.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.catalog.Catalog;
import me.spolzer.inorders.catalog.ItemLabels;
import me.spolzer.inorders.menu.BrowseMenu;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The item catalog as a dialog. The same list serves two purposes: picking what to order,
 * and picking an item to see the orders for it.
 */
public final class CatalogDialog {
	private static final int COLUMNS = 4;
	private static final int ROWS = 8;
	private static final int BUTTON = 180;
	// Gap the client leaves between grid cells, needed to line the search field up with the buttons
	private static final int GAP = 2;

	private final InOrdersPlugin plugin;
	// Only for "Back" from the order form, every new opening starts on the first page
	private final Map<UUID, View> last = new HashMap<>();

	// back is the order list to return to when finding orders, null when creating one
	private record View(int tab, String search, int page, BrowseMenu.State back) {

		boolean finding() {
			return back != null;
		}
	}

	public CatalogDialog(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	public void create(Player player) {
		show(player, new View(0, null, 0, null));
	}

	public void find(Player player, BrowseMenu.State back) {
		show(player, new View(0, null, 0, back));
	}

	public void resume(Player player) {
		View view = last.get(player.getUniqueId());
		if (view == null) create(player);
		else show(player, view);
	}

	public void forget(UUID player) {
		last.remove(player);
	}

	private void show(Player player, View view) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		List<Catalog.Tab> tabs = plugin.catalog().tabs();
		if (tabs.isEmpty()) return;
		int pageSize = COLUMNS * ROWS;
		int tab = Math.clamp(view.tab(), 0, tabs.size() - 1);
		List<Catalog.Entry> items = view.search() != null ? plugin.catalog().search(view.search()) : tabs.get(tab).items();
		int pages = Math.max(1, (items.size() + pageSize - 1) / pageSize);
		int page = Math.clamp(view.page(), 0, pages - 1);
		View shown = new View(tab, view.search(), page, view.back());
		last.put(player.getUniqueId(), shown);

		String line = view.search() == null ? "catalog.section" : items.isEmpty() ? "catalog.nothing" : "catalog.found";
		List<Component> text = List.of(messages.get(player, line, Arg.of("query", view.search() == null ? "" : view.search()),
				Arg.of("count", items.size()), Arg.of("section", tabs.get(tab).name()), Arg.of("page", page + 1), Arg.of("pages", pages)));

		ItemLabels labels = plugin.itemLabels();
		ActionButton search = dialogs.button(labels.label(player, new ItemStack(Material.SPYGLASS), messages.get(player, "catalog.search")),
				null, BUTTON, response -> {
					String query = response.getText("search");
					query = query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);
					show(player, new View(tab, query, 0, view.back()));
				});
		ActionButton sections = dialogs.button(labels.label(player, new ItemStack(Material.BOOK), messages.get(player, "catalog.sections")),
				null, BUTTON, response -> sections(player, shown));
		// Page buttons that lead nowhere stay in place, greyed out, so the toolbar never shifts
		ActionButton previous = dialogs.button(messages.get(player, page > 0 ? "catalog.previous" : "catalog.no-previous"), null, BUTTON,
				response -> show(player, new View(tab, view.search(), Math.max(0, page - 1), view.back())));
		ActionButton next = dialogs.button(messages.get(player, page < pages - 1 ? "catalog.next" : "catalog.no-next"), null, BUTTON,
				response -> show(player, new View(tab, view.search(), Math.min(pages - 1, page + 1), view.back())));

		// The toolbar fills exactly one row, otherwise the item buttons would start in the middle of it
		List<ActionButton> actions = new ArrayList<>(List.of(previous, search, sections, next));
		for (int i = page * pageSize; i < Math.min(items.size(), (page + 1) * pageSize); i++) {
			ItemStack item = items.get(i).item();
			actions.add(dialogs.button(labels.label(player, item), ItemLabels.tooltip(player, item), BUTTON, response -> choose(player, item, shown)));
		}

		int row = COLUMNS * BUTTON + (COLUMNS - 1) * GAP;
		DialogInput field = DialogInput.text("search", messages.get(player, "catalog.search-label"))
				.initial(view.search() == null ? "" : view.search())
				.maxLength(32)
				.width(row)
				.build();
		ActionButton close = dialogs.button(messages.get(player, "dialog.close"),
				() -> plugin.screens().browse(player, view.finding() ? view.back() : BrowseMenu.State.first()));
		dialogs.show(player, messages.get(player, view.finding() ? "catalog.find-title" : "picker.title"), Dialogs.body(null, text, row),
				List.of(field), DialogType.multiAction(actions, close, COLUMNS));
	}

	private void sections(Player player, View from) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		ItemLabels labels = plugin.itemLabels();
		List<ActionButton> actions = new ArrayList<>();
		List<Catalog.Tab> tabs = plugin.catalog().tabs();
		for (int i = 0; i < tabs.size(); i++) {
			int index = i;
			actions.add(dialogs.button(labels.label(player, tabs.get(i).icon(), tabs.get(i).name()), null, BUTTON,
					response -> show(player, new View(index, null, 0, from.back()))));
		}
		actions.add(dialogs.button(labels.label(player, new ItemStack(Material.BUNDLE), messages.get(player, "catalog.inventory")),
				null, BUTTON, response -> inventory(player, from)));
		dialogs.show(player, messages.get(player, "catalog.sections-title"), List.of(), List.of(),
				DialogType.multiAction(actions, dialogs.button(messages.get(player, "dialog.back"), () -> show(player, from)), COLUMNS));
	}

	private void inventory(Player player, View from) {
		Messages messages = plugin.messages();
		Dialogs dialogs = plugin.dialogs();
		ItemLabels labels = plugin.itemLabels();
		Set<ItemStack> templates = new LinkedHashSet<>();
		for (ItemStack stack : player.getInventory().getStorageContents()) {
			if (stack != null && !stack.isEmpty()) templates.add(Items.template(stack));
		}
		List<ActionButton> actions = new ArrayList<>();
		for (ItemStack item : templates) {
			actions.add(dialogs.button(labels.label(player, item), ItemLabels.tooltip(player, item), BUTTON, response -> choose(player, item, from)));
		}
		String hint = templates.isEmpty() ? "catalog.inventory-empty" : from.finding() ? "catalog.inventory-find" : "catalog.inventory-hint";
		ActionButton back = dialogs.button(messages.get(player, "dialog.back"), () -> sections(player, from));
		// A multi-action dialog needs at least one action
		dialogs.show(player, messages.get(player, "catalog.inventory"), Dialogs.body(null, messages.lore(player, hint)), List.of(),
				actions.isEmpty() ? DialogType.notice(back) : DialogType.multiAction(actions, back, COLUMNS));
	}

	private void choose(Player player, ItemStack item, View from) {
		if (from.finding()) {
			BrowseMenu.State back = from.back();
			plugin.screens().browse(player, new BrowseMenu.State(back.query().withItem(item, plugin.names().searchText(item)), 0));
			return;
		}
		if (plugin.settings().isBlocked(item.getType())) {
			plugin.messages().send(player, "create.blocked");
			plugin.sounds().play(player, "error");
			show(player, from);
			return;
		}
		plugin.screens().create(player, plugin.screens().draft(player, item));
	}
}
