package me.spolzer.inorders.order;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

public final class Items {
	private Items() {}

	/**
	 * Turns an inventory item into an order template. Wear, anvil cost and container contents are dropped.
	 * The name is kept only for items marked by plugins, a renamed vanilla item would never be delivered.
	 */
	public static ItemStack template(ItemStack source) {
		ItemStack item = source.clone();
		item.setAmount(1);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return item;
		if (meta instanceof BlockStateMeta state && state.hasBlockState() && state.getBlockState() instanceof Container) {
			return new ItemStack(item.getType());
		}
		// Every meta implements these interfaces, so they are only touched when set:
		// an explicit zero would become a component a plain item does not have
		if (meta instanceof BundleMeta bundle && bundle.hasItems()) bundle.setItems(null);
		if (meta instanceof Damageable damageable && damageable.hasDamage()) damageable.setDamage(0);
		if (meta instanceof Repairable repairable && repairable.hasRepairCost()) repairable.setRepairCost(0);
		if (meta.getPersistentDataContainer().isEmpty()) meta.displayName(null);
		item.setItemMeta(meta);
		return item;
	}

	public static boolean matches(ItemStack template, ItemStack candidate) {
		if (candidate == null || candidate.getType() != template.getType()) return false;
		if (template.isSimilar(candidate)) return true;
		// Enchanted books and gear that went through an anvil differ only by the repair cost
		if (!(candidate.getItemMeta() instanceof Repairable repairable) || repairable.getRepairCost() == 0) return false;
		ItemStack reset = candidate.clone();
		reset.editMeta(Repairable.class, meta -> meta.setRepairCost(0));
		return template.isSimilar(reset);
	}

	public static int count(Player player, ItemStack template) {
		int total = 0;
		for (ItemStack item : player.getInventory().getStorageContents()) {
			if (matches(template, item)) total += item.getAmount();
		}
		return total;
	}

	public static List<ItemStack> take(Player player, ItemStack template, int count) {
		PlayerInventory inventory = player.getInventory();
		ItemStack[] contents = inventory.getStorageContents();
		List<ItemStack> taken = new ArrayList<>();
		int left = count;
		for (int slot = 0; slot < contents.length && left > 0; slot++) {
			ItemStack item = contents[slot];
			if (!matches(template, item)) continue;
			int part = Math.min(left, item.getAmount());
			ItemStack piece = item.clone();
			piece.setAmount(part);
			taken.add(piece);
			left -= part;
			if (part == item.getAmount()) contents[slot] = null;
			else item.setAmount(item.getAmount() - part);
		}
		inventory.setStorageContents(contents);
		return taken;
	}

	public static int space(Player player, ItemStack template) {
		int max = template.getMaxStackSize();
		int space = 0;
		for (ItemStack item : player.getInventory().getStorageContents()) {
			if (item == null || item.isEmpty()) space += max;
			else if (template.isSimilar(item)) space += Math.max(0, max - item.getAmount());
		}
		return space;
	}

	public static void give(Player player, List<ItemStack> items) {
		if (items.isEmpty()) return;
		UUID owner = player.getUniqueId();
		for (ItemStack rest : player.getInventory().addItem(items.toArray(new ItemStack[0])).values()) {
			player.getWorld().dropItem(player.getLocation(), rest, drop -> drop.setOwner(owner));
		}
	}

	public static List<ItemStack> stacks(ItemStack template, int count) {
		List<ItemStack> stacks = new ArrayList<>();
		int max = template.getMaxStackSize();
		while (count > 0) {
			ItemStack stack = template.clone();
			stack.setAmount(Math.min(max, count));
			count -= stack.getAmount();
			stacks.add(stack);
		}
		return stacks;
	}

	/** Cuts the stacks after the first {@code count} items, a stack on the border is divided. */
	public static Split split(List<ItemStack> stacks, int count) {
		List<ItemStack> first = new ArrayList<>();
		List<ItemStack> rest = new ArrayList<>();
		int left = Math.max(0, count);
		for (ItemStack stack : stacks) {
			int part = Math.min(left, stack.getAmount());
			if (part > 0) first.add(withAmount(stack, part));
			if (part < stack.getAmount()) rest.add(withAmount(stack, stack.getAmount() - part));
			left -= part;
		}
		return new Split(first, rest);
	}

	public static int total(List<ItemStack> stacks) {
		return stacks.stream().mapToInt(ItemStack::getAmount).sum();
	}

	private static ItemStack withAmount(ItemStack stack, int amount) {
		ItemStack piece = stack.clone();
		piece.setAmount(amount);
		return piece;
	}

	public record Split(List<ItemStack> first, List<ItemStack> rest) {}
}
