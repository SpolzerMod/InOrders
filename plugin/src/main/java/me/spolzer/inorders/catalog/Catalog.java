package me.spolzer.inorders.catalog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.spolzer.inorders.config.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Items players can order, grouped like the creative inventory. The tabs are built by the server code,
 * which Paper does not expose, so they are read by reflection. If that fails, one alphabetical tab is used.
 */
public final class Catalog {
	private volatile List<Tab> tabs = List.of();
	private volatile List<Entry> everything = List.of();

	public record Tab(String id, Component name, ItemStack icon, List<Entry> items) {}

	public record Entry(ItemStack item, String search) {}

	public void load(Settings settings, Names names, Logger logger) {
		List<Tab> loaded;
		try {
			loaded = CreativeTabs.read(settings, names);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			logger.log(Level.WARNING, "Could not read the creative tabs, the item catalog shows all items in one list", e);
			loaded = List.of(alphabetical(settings, names));
		}
		List<Entry> all = new ArrayList<>();
		Set<ItemStack> seen = new HashSet<>();
		for (Tab tab : loaded) {
			for (Entry entry : tab.items()) {
				if (seen.add(entry.item())) all.add(entry);
			}
		}
		tabs = List.copyOf(loaded);
		everything = List.copyOf(all);
	}

	public List<Tab> tabs() { return tabs; }
	public List<Entry> everything() { return everything; }

	public List<Entry> search(String text) {
		String query = text.toLowerCase(Locale.ROOT).trim();
		// The shortest description is the plainest match: "diamond" before "diamond block" and "diamond ore"
		return everything.stream()
				.filter(entry -> entry.search().contains(query))
				.sorted(Comparator.comparingInt(entry -> entry.search().length()))
				.toList();
	}

	private static Tab alphabetical(Settings settings, Names names) {
		List<Entry> items = new ArrayList<>();
		for (Material material : Material.values()) {
			if (material.isLegacy() || !material.isItem() || material.isAir() || settings.isBlocked(material)) continue;
			ItemStack item = new ItemStack(material);
			items.add(new Entry(item, names.searchText(item)));
		}
		items.sort(Comparator.comparing(entry -> entry.item().getType().getKey().getKey()));
		return new Tab("all", Component.translatable("itemGroup.search"), new ItemStack(Material.COMPASS), List.copyOf(items));
	}

	private static final class CreativeTabs {

		static List<Tab> read(Settings settings, Names names) throws ReflectiveOperationException {
			Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
			Object server = serverClass.getMethod("getServer").invoke(null);
			Object registries = serverClass.getMethod("registryAccess").invoke(server);
			Object worldData = serverClass.getMethod("getWorldData").invoke(server);
			Object features = worldData.getClass().getMethod("enabledFeatures").invoke(worldData);

			Class<?> tabsClass = Class.forName("net.minecraft.world.item.CreativeModeTabs");
			method(tabsClass, "tryRebuildTabContents", 3).invoke(null, features, false, registries);
			List<Method> toBukkit = methods(Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack"), "asBukkitCopy", 1);

			List<Tab> tabs = new ArrayList<>();
			for (Object tab : (List<?>) tabsClass.getMethod("tabs").invoke(null)) {
				Object type = tab.getClass().getMethod("getType").invoke(tab);
				if (!((Enum<?>) type).name().equals("CATEGORY")) continue;

				Object name = tab.getClass().getMethod("getDisplayName").invoke(tab);
				Object contents = name.getClass().getMethod("getContents").invoke(name);
				String key = (String) contents.getClass().getMethod("getKey").invoke(contents);
				String id = id(key);
				if (settings.hiddenTabs.contains(id)) continue;

				List<Entry> items = new ArrayList<>();
				for (Object stack : (Collection<?>) tab.getClass().getMethod("getDisplayItems").invoke(tab)) {
					ItemStack item = convert(toBukkit, stack);
					if (item.isEmpty() || settings.isBlocked(item.getType())) continue;
					item.setAmount(1);
					items.add(new Entry(item, names.searchText(item)));
				}
				if (items.isEmpty()) continue;
				ItemStack icon = convert(toBukkit, tab.getClass().getMethod("getIconItem").invoke(tab));
				tabs.add(new Tab(id, Component.translatable(key), icon, List.copyOf(items)));
			}
			if (tabs.isEmpty()) throw new IllegalStateException("The server returned no creative tabs");
			return tabs;
		}

		// itemGroup.buildingBlocks -> building_blocks
		private static String id(String key) {
			String name = key.substring(key.lastIndexOf('.') + 1);
			StringBuilder id = new StringBuilder();
			for (char c : name.toCharArray()) {
				if (Character.isUpperCase(c)) id.append('_').append(Character.toLowerCase(c));
				else id.append(c);
			}
			return id.toString();
		}

		private static Method method(Class<?> owner, String name, int parameters) throws NoSuchMethodException {
			List<Method> found = methods(owner, name, parameters);
			if (found.isEmpty()) throw new NoSuchMethodException(owner.getName() + "." + name);
			return found.getFirst();
		}

		// Parameter types change between versions (26.1 has two asBukkitCopy overloads, 26.3 takes ItemInstance),
		// so methods are matched by name and count and the overload is picked by the argument
		private static List<Method> methods(Class<?> owner, String name, int parameters) {
			List<Method> found = new ArrayList<>();
			for (Method method : owner.getMethods()) {
				if (method.getName().equals(name) && method.getParameterCount() == parameters) found.add(method);
			}
			return found;
		}

		private static ItemStack convert(List<Method> overloads, Object stack) throws ReflectiveOperationException {
			for (Method method : overloads) {
				if (method.getParameterTypes()[0].isInstance(stack)) return (ItemStack) method.invoke(null, stack);
			}
			throw new NoSuchMethodException("asBukkitCopy for " + stack.getClass().getName());
		}
	}
}
