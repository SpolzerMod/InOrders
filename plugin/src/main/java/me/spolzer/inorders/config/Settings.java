package me.spolzer.inorders.config;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import me.spolzer.inorders.currency.Money;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class Settings {
	public final String language;
	public final String defaultLanguage;
	public final boolean openToEveryone;
	public final FormMode forms;
	public final Set<String> hiddenTabs = new HashSet<>();
	public final int maxActive;
	public final int maxAmount;
	public final List<Integer> durations = new ArrayList<>();
	public final int defaultDuration;
	public final double feePercent;
	public final double taxPercent;
	public final boolean notifyDeliveries;
	public final boolean joinReminder;
	public final Database database;

	private final Map<String, CurrencySettings> currencies = new HashMap<>();
	private final Set<Material> blockedItems = EnumSet.noneOf(Material.class);
	private final Set<String> disabledWorlds = new HashSet<>();
	private final Map<String, Sound> sounds = new HashMap<>();

	public Settings(FileConfiguration config, Logger logger) {
		language = config.getString("language", "auto");
		defaultLanguage = config.getString("default-language", "en");
		openToEveryone = config.getBoolean("permissions.open-to-everyone", true);
		forms = FormMode.parse(config.getString("menus.forms", "auto"), logger);
		for (String tab : config.getStringList("menus.hidden-tabs")) hiddenTabs.add(tab.toLowerCase(Locale.ROOT));

		maxActive = Math.max(1, config.getInt("orders.max-active", 7));
		maxAmount = Math.max(1, config.getInt("orders.max-amount", 2304));
		for (int days : config.getIntegerList("orders.durations")) {
			if (days > 0 && !durations.contains(days)) durations.add(days);
		}
		if (durations.isEmpty()) durations.add(3);
		int preferred = config.getInt("orders.default-duration", 3);
		defaultDuration = durations.contains(preferred) ? preferred : durations.getFirst();
		feePercent = Math.clamp(config.getDouble("orders.fee-percent", 0), 0, 100);
		taxPercent = Math.clamp(config.getDouble("orders.tax-percent", 0), 0, 100);

		notifyDeliveries = config.getBoolean("notifications.deliveries", true);
		joinReminder = config.getBoolean("notifications.join-reminder", true);
		database = new Database(config, logger);

		for (String id : List.of("money", "playerpoints")) {
			String path = "currencies." + id;
			currencies.put(id, new CurrencySettings(config.getBoolean(path + ".enabled", true),
					Math.max(0, Money.of(config.getDouble(path + ".min-price", id.equals("money") ? 0.01 : 1))),
					Math.max(0, Money.of(config.getDouble(path + ".max-price", 0)))));
		}
		for (String name : config.getStringList("blocked-items")) {
			Material material = Material.matchMaterial(name);
			if (material != null) blockedItems.add(material);
			else logger.warning("Unknown material in blocked-items: " + name);
		}
		for (String world : config.getStringList("disabled-worlds")) disabledWorlds.add(world.toLowerCase(Locale.ROOT));

		ConfigurationSection soundSection = config.getConfigurationSection("sounds");
		if (soundSection != null) {
			for (String name : soundSection.getKeys(false)) {
				Sound sound = sound(soundSection.getString(name, ""), logger);
				if (sound != null) sounds.put(name, sound);
			}
		}
	}

	public CurrencySettings currency(String id) {
		return currencies.getOrDefault(id, new CurrencySettings(false, 0, 0));
	}

	public boolean isBlocked(Material material) { return blockedItems.contains(material); }
	public boolean isWorldDisabled(String world) { return disabledWorlds.contains(world.toLowerCase(Locale.ROOT)); }
	public Sound sound(String name) { return sounds.get(name); }

	// "ui.button.click 0.6 1.2": key, volume, pitch. An empty value turns the sound off
	private static Sound sound(String value, Logger logger) {
		String[] parts = value.trim().split("\s+");
		if (parts[0].isEmpty()) return null;
		try {
			float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1f;
			float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1f;
			return Sound.sound(Key.key(parts[0].toLowerCase(Locale.ROOT)), Sound.Source.MASTER, volume, pitch);
		} catch (InvalidKeyException | NumberFormatException e) {
			logger.warning("Invalid sound '" + value + "', expected: <key> [volume] [pitch]");
			return null;
		}
	}

	public record CurrencySettings(boolean enabled, long minPrice, long maxPrice) {}

	public enum FormMode {
		AUTO, DIALOG, CHEST;

		static FormMode parse(String value, Logger logger) {
			try {
				return valueOf(value.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				logger.warning("Unknown menus.forms '" + value + "', using auto");
				return AUTO;
			}
		}
	}

	public static final class Database {
		public final boolean mysql;
		public final String serverId;
		public final boolean shared;
		public final String host;
		public final int port;
		public final String name;
		public final String user;
		public final String password;
		public final int poolSize;
		public final Map<String, String> properties = new LinkedHashMap<>();

		Database(FileConfiguration config, Logger logger) {
			String type = config.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
			mysql = type.equals("mysql") || type.equals("mariadb");
			if (!mysql && !type.equals("sqlite")) logger.warning("Unknown storage.type '" + type + "', using sqlite");
			serverId = config.getString("storage.server-id", "main");
			shared = mysql && config.getBoolean("storage.shared-orders", false);
			host = config.getString("storage.mysql.host", "localhost");
			port = config.getInt("storage.mysql.port", 3306);
			name = config.getString("storage.mysql.database", "minecraft");
			user = config.getString("storage.mysql.user", "root");
			password = config.getString("storage.mysql.password", "");
			poolSize = Math.max(2, config.getInt("storage.mysql.pool-size", 4));
			ConfigurationSection extra = config.getConfigurationSection("storage.mysql.properties");
			if (extra != null) {
				for (String key : extra.getKeys(false)) properties.put(key, extra.getString(key));
			}
		}
	}
}
