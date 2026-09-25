package me.spolzer.inorders.menu;

import java.util.List;
import java.util.Locale;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.catalog.Catalog;
import me.spolzer.inorders.order.Items;
import me.spolzer.inorders.text.Arg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class PickerMenu extends Menu {
	private static final int TABS = 9;
	private static final int FIRST = 9;
	private static final int PAGE_SIZE = 36;
	private static final int PREVIOUS = 45;
	private static final int SEARCH = 47;
	private static final int HINT = 49;
	private static final int BACK = 51;
	private static final int NEXT = 53;

	private int tab;
	private int page;
	private String search;
	private List<Catalog.Entry> results = List.of();

	public PickerMenu(InOrdersPlugin plugin, Player viewer) {
		super(plugin, viewer, 6, "picker.title");
	}

	public void show() {
		render();
		open();
	}

	private void render() {
		clear();
		List<Catalog.Tab> tabs = plugin.catalog().tabs();
		for (int i = 0; i < Math.min(TABS, tabs.size()); i++) {
			Catalog.Tab entry = tabs.get(i);
			boolean selected = search == null && i == tab;
			Component name = entry.name().colorIfAbsent(selected ? NamedTextColor.GOLD : NamedTextColor.GRAY)
					.decoration(TextDecoration.ITALIC, false);
			int index = i;
			set(i, Icons.glowing(Icons.named(entry.icon(), name,
					messages.lore(viewer, selected ? "picker.tab.selected" : "picker.tab.lore", Arg.of("count", entry.items().size()))), selected),
					click -> {
						tab = index;
						page = 0;
						search = null;
						render();
					});
		}

		List<Catalog.Entry> items = search != null ? results : tabs.isEmpty() ? List.of() : tabs.get(Math.min(tab, tabs.size() - 1)).items();
		int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= items.size()) break;
			ItemStack item = items.get(index).item();
			set(FIRST + i, Icons.describe(item, 1, messages.lore(viewer, "picker.item")), click -> choose(item));
		}
		if (search != null && items.isEmpty()) {
			set(31, Icons.icon(Material.PAPER, messages.item(viewer, "picker.nothing", Arg.of("query", search)),
					messages.lore(viewer, "picker.nothing-lore")));
		}

		fill(45, 53, Material.BLACK_STAINED_GLASS_PANE);
		Arg pageArg = Arg.of("page", page + 1);
		Arg pagesArg = Arg.of("pages", pages);
		if (page > 0) {
			set(PREVIOUS, Icons.icon(Material.ARROW, messages.item(viewer, "picker.previous", pageArg, pagesArg)), click -> {
				page--;
				render();
			});
		}
		if (page < pages - 1) {
			set(NEXT, Icons.icon(Material.ARROW, messages.item(viewer, "picker.next", pageArg, pagesArg)), click -> {
				page++;
				render();
			});
		}
		ItemStack searchIcon = search == null
				? Icons.icon(Material.SPYGLASS, messages.item(viewer, "picker.search.name"), messages.lore(viewer, "picker.search.lore"))
				: Icons.glowing(Icons.icon(Material.SPYGLASS, messages.item(viewer, "picker.search.active", Arg.of("query", search)),
						messages.lore(viewer, "picker.search.active-lore", Arg.of("count", results.size()))), true);
		set(SEARCH, searchIcon, this::clickSearch);
		set(HINT, Icons.icon(Material.CHEST, messages.item(viewer, "picker.hint.name"), messages.lore(viewer, "picker.hint.lore")));
		set(BACK, Icons.icon(Material.OAK_DOOR, messages.item(viewer, "picker.back")), click -> later(() -> plugin.screens().browse(viewer)));
	}

	private void clickSearch(ClickType click) {
		if (click.isRightClick() && search != null) {
			search = null;
			page = 0;
			render();
			return;
		}
		later(() -> plugin.input().ask(viewer, "picker.search.input", search == null ? "" : search, text -> {
			if (text != null && !text.isBlank()) {
				search = text.trim().toLowerCase(Locale.ROOT);
				results = plugin.catalog().search(search);
				page = 0;
			}
			show();
		}));
	}

	@Override
	protected void clickInventory(ItemStack item, ClickType click) {
		choose(Items.template(item));
	}

	private void choose(ItemStack item) {
		if (plugin.settings().isBlocked(item.getType())) {
			messages.send(viewer, "create.blocked");
			plugin.sounds().play(viewer, "error");
			return;
		}
		later(() -> plugin.screens().create(viewer, plugin.screens().draft(viewer, item)));
	}
}
