package me.spolzer.inorders.menu;

import java.util.HashMap;
import java.util.Map;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class Menu implements InventoryHolder {
	protected final InOrdersPlugin plugin;
	protected final Player viewer;
	protected final Messages messages;
	private final Inventory inventory;
	private final Map<Integer, Button> buttons = new HashMap<>();

	@FunctionalInterface
	public interface Button {
		void click(ClickType click);
	}

	protected Menu(InOrdersPlugin plugin, Player viewer, int rows, String titleKey, Arg... args) {
		this.plugin = plugin;
		this.viewer = viewer;
		this.messages = plugin.messages();
		this.inventory = Bukkit.createInventory(this, rows * 9, messages.get(viewer, titleKey, args));
	}

	@Override
	public @NotNull Inventory getInventory() { return inventory; }

	public void open() {
		viewer.openInventory(inventory);
	}

	public boolean isOpen() {
		return viewer.isOnline() && viewer.getOpenInventory().getTopInventory() == inventory;
	}

	protected void set(int slot, ItemStack item, Button button) {
		inventory.setItem(slot, item);
		if (button != null) buttons.put(slot, button);
		else buttons.remove(slot);
	}

	protected void set(int slot, ItemStack item) {
		set(slot, item, null);
	}

	protected void fill(int from, int to, Material material) {
		ItemStack filler = Icons.filler(material);
		for (int slot = from; slot <= to; slot++) set(slot, filler);
	}

	protected void clear() {
		inventory.clear();
		buttons.clear();
	}

	protected int size() {
		return inventory.getSize();
	}

	/** Opening another inventory inside a click event is unsafe, so screen changes wait a tick. */
	protected void later(Runnable task) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (viewer.isOnline()) task.run();
		});
	}

	public void handleClick(InventoryClickEvent event) {
		event.setCancelled(true);
		if (event.getClickedInventory() == inventory) {
			Button button = buttons.get(event.getSlot());
			if (button == null) return;
			plugin.sounds().play(viewer, "click");
			button.click(event.getClick());
		} else if (event.getClickedInventory() != null && event.getCurrentItem() != null && !event.getCurrentItem().isEmpty()) {
			clickInventory(event.getCurrentItem(), event.getClick());
		}
	}

	protected void clickInventory(ItemStack item, ClickType click) {}
}
