package me.spolzer.inorders.menu;

import java.util.List;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.text.Arg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ConfirmMenu extends Menu {

	public ConfirmMenu(InOrdersPlugin plugin, Player viewer, String titleKey, Arg[] titleArgs, ItemStack subject, List<Component> lines,
			Component yes, Runnable onYes, Component no, Runnable onNo) {
		super(plugin, viewer, 3, titleKey, titleArgs);
		set(13, Icons.describe(subject, subject.getAmount(), lines));
		set(11, Icons.icon(Material.LIME_CONCRETE, yes), click -> later(onYes));
		set(15, Icons.icon(Material.RED_CONCRETE, no), click -> later(onNo));
	}
}
