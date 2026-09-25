package me.spolzer.inorders.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

/**
 * Item names in the bundled languages. The server only knows English names, and players search
 * in their own language, so the vanilla names are shipped with the plugin (assets/names_*.tsv).
 */
public final class Names {
	private static final String[] LANGUAGES = {"en", "ru"};

	private volatile List<Map<String, String>> tables = List.of();

	public void load(Plugin plugin, Logger logger) {
		List<Map<String, String>> tables = new ArrayList<>();
		for (String language : LANGUAGES) {
			Map<String, String> table = new HashMap<>();
			try (InputStream stream = plugin.getResource("assets/names_" + language + ".tsv")) {
				if (stream == null) continue;
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
				for (String line = reader.readLine(); line != null; line = reader.readLine()) {
					int tab = line.indexOf('\t');
					if (tab > 0) table.put(line.substring(0, tab), line.substring(tab + 1));
				}
			} catch (IOException e) {
				logger.warning("Could not read item names for " + language + ": " + e.getMessage());
			}
			tables.add(table);
		}
		this.tables = List.copyOf(tables);
	}

	public String searchText(ItemStack item) {
		List<String> keys = new ArrayList<>();
		keys.add(item.getType().translationKey());
		ItemMeta meta = item.getItemMeta();
		if (meta instanceof EnchantmentStorageMeta book) {
			for (Enchantment enchantment : book.getStoredEnchants().keySet()) keys.add(key(enchantment));
		}
		if (meta != null) {
			for (Enchantment enchantment : meta.getEnchants().keySet()) keys.add(key(enchantment));
		}
		if (meta instanceof PotionMeta potion && potion.getBasePotionType() != null) keys.add(potionKey(item, potion.getBasePotionType()));

		String id = item.getType().getKey().getKey();
		StringBuilder text = new StringBuilder(id).append(' ').append(id.replace('_', ' '));
		for (String key : keys) {
			for (Map<String, String> table : tables) {
				String name = table.get(key);
				if (name != null) text.append(' ').append(name);
			}
		}
		return text.toString().toLowerCase(Locale.ROOT);
	}

	// Enchantment#translationKey is marked for removal, the key follows the vanilla pattern anyway
	private static String key(Enchantment enchantment) {
		return "enchantment." + enchantment.getKey().getNamespace() + "." + enchantment.getKey().getKey();
	}

	// item.minecraft.splash_potion.effect.swiftness: the long and strong variants share the base name
	private static String potionKey(ItemStack item, PotionType type) {
		String effect = type.getKey().getKey().replaceFirst("^(long|strong)_", "");
		return "item.minecraft." + item.getType().getKey().getKey() + ".effect." + effect;
	}
}
