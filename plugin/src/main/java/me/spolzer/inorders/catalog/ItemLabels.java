package me.spolzer.inorders.catalog;

import io.papermc.paper.inventory.tooltip.TooltipContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import me.spolzer.inorders.input.Clients;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.Plugin;

public final class ItemLabels {
	// 1.21.11: the first version where item textures have their own atlas, which assets/sprites.tsv refers to
	private static final int SPRITE_PROTOCOL = 774;

	private volatile Map<String, String> sprites = Map.of();
	private final MiniMessage mini = MiniMessage.miniMessage();
	private final Clients clients;

	public ItemLabels(Clients clients) {
		this.clients = clients;
	}

	public void load(Plugin plugin, Logger logger) {
		if (!spritesInText()) return;
		Map<String, String> sprites = new HashMap<>();
		try (InputStream stream = plugin.getResource("assets/sprites.tsv")) {
			if (stream == null) return;
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String[] parts = line.split("\t");
				if (parts.length == 3) sprites.put(parts[0], "<sprite:" + parts[1] + ":" + parts[2] + ">");
			}
		} catch (IOException e) {
			logger.warning("Could not read item sprites: " + e.getMessage());
		}
		this.sprites = Map.copyOf(sprites);
	}

	public Component label(Player player, ItemStack item) {
		return label(player, item, name(item));
	}

	public Component label(Player player, ItemStack item, Component text) {
		Component name = text.colorIfAbsent(NamedTextColor.WHITE);
		String sprite = sprites.get(item.getType().getKey().getKey());
		if (sprite == null || !clients.protocolAtLeast(player, SPRITE_PROTOCOL)) return name;
		return Component.text().append(mini.deserialize(sprite)).append(Component.text("  ")).append(name).build();
	}

	/** The item name, with the enchantment for enchanted books: every book is called the same otherwise. */
	public static Component name(ItemStack item) {
		Component name = item.effectiveName();
		if (item.getItemMeta() instanceof EnchantmentStorageMeta book && book.hasStoredEnchants()) {
			List<Component> enchantments = book.getStoredEnchants().entrySet().stream()
					.map(entry -> entry.getKey().displayName(entry.getValue()))
					.toList();
			name = Component.join(JoinConfiguration.separator(Component.text(", ")), enchantments);
		}
		return name;
	}

	public static Component tooltip(Player player, ItemStack item) {
		List<Component> lines = item.computeTooltipLines(TooltipContext.create(false, false), player);
		if (lines.size() <= 1) return null;
		return Component.join(JoinConfiguration.newlines(), lines.subList(1, lines.size()));
	}

	private static boolean spritesInText() {
		try {
			Class.forName("net.kyori.adventure.text.ObjectComponent");
		} catch (ClassNotFoundException e) {
			return false;
		}
		int[] version = new int[3];
		String[] parts = Bukkit.getMinecraftVersion().split("[^0-9]+");
		for (int i = 0; i < Math.min(3, parts.length); i++) {
			if (!parts[i].isEmpty()) version[i] = Integer.parseInt(parts[i]);
		}
		return version[0] > 1 || version[1] > 21 || (version[1] == 21 && version[2] >= 11);
	}
}
