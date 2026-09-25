package me.spolzer.inorders.listener;

import me.spolzer.inorders.menu.Menu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST)
	public void onClick(InventoryClickEvent event) {
		if (event.getInventory().getHolder(false) instanceof Menu menu) menu.handleClick(event);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDrag(InventoryDragEvent event) {
		if (event.getInventory().getHolder(false) instanceof Menu) event.setCancelled(true);
	}
}
