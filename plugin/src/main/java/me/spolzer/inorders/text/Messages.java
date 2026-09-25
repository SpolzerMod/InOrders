package me.spolzer.inorders.text;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {
	private static final String[] BUNDLED = {"en", "ru"};

	private final JavaPlugin plugin;
	private final MiniMessage mini = MiniMessage.miniMessage();
	private volatile Loaded loaded;

	private record Loaded(Map<String, Lang> langs, Lang fallback, boolean perPlayer) {

		Lang find(String code) {
			Lang lang = langs.get(code);
			if (lang == null && code.contains("_")) lang = langs.get(code.substring(0, code.indexOf('_')));
			return lang;
		}
	}

	public Messages(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public void load(String language, String defaultLanguage) {
		File folder = new File(plugin.getDataFolder(), "lang");
		for (String code : BUNDLED) {
			if (!new File(folder, code + ".yml").exists()) plugin.saveResource("lang/" + code + ".yml", false);
		}

		Map<String, Lang> langs = new HashMap<>();
		YamlConfiguration english = bundled("en");
		File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
		if (files != null) {
			for (File file : files) {
				String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
				YamlConfiguration defaults = bundled(code);
				langs.put(code, new Lang(Locale.forLanguageTag(code.replace('_', '-')),
						YamlConfiguration.loadConfiguration(file), defaults != null ? defaults : english, english));
			}
		}

		boolean perPlayer = language.equalsIgnoreCase("auto");
		String forced = perPlayer ? defaultLanguage : language;
		Lang fallback = new Loaded(langs, null, perPlayer).find(forced.toLowerCase(Locale.ROOT));
		if (fallback == null) {
			plugin.getLogger().warning("lang/" + forced + ".yml not found, using en");
			fallback = langs.get("en");
		}
		loaded = new Loaded(Map.copyOf(langs), fallback, perPlayer);
	}

	private YamlConfiguration bundled(String code) {
		InputStream stream = plugin.getResource("lang/" + code + ".yml");
		if (stream == null) return null;
		return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
	}

	private Lang lang(CommandSender to) {
		Loaded current = loaded;
		if (current.perPlayer() && to instanceof Player player) {
			Lang own = current.find(player.locale().toString().toLowerCase(Locale.ROOT));
			if (own != null) return own;
		}
		return current.fallback();
	}

	public Locale locale() {
		return loaded.fallback().locale;
	}

	public Locale locale(CommandSender to) {
		return lang(to).locale;
	}

	private String raw(CommandSender to, String key) {
		return lang(to).raw(key);
	}

	public Component get(CommandSender to, String key, Arg... args) {
		return parse(raw(to, key), args);
	}

	public Component item(CommandSender to, String key, Arg... args) {
		return parse("<!i>" + raw(to, key), args);
	}

	public List<Component> lore(CommandSender to, String key, Arg... args) {
		List<Component> lines = new ArrayList<>();
		for (String line : lang(to).list(key)) lines.add(parse("<!i>" + line, args));
		return lines;
	}

	public List<String> list(CommandSender to, String key) {
		return lang(to).list(key);
	}

	public String markup(CommandSender to, String key, Arg... args) {
		String text = raw(to, key);
		for (Arg arg : args) {
			if (arg.text != null) text = text.replace("<" + arg.name + ">", arg.markup ? arg.text : mini.escapeTags(arg.text));
		}
		return text;
	}

	public String plain(CommandSender to, String key, Arg... args) {
		String text = raw(to, key);
		for (Arg arg : args) {
			if (arg.text != null) text = text.replace("<" + arg.name + ">", arg.text);
		}
		return mini.stripTags(text);
	}

	public Arg currency(CommandSender to, String id) {
		return Arg.markup("currency", raw(to, "currency." + id));
	}

	public void send(CommandSender to, String key, Arg... args) {
		String text = raw(to, key);
		if (text.isEmpty()) return;
		to.sendMessage(parse(raw(to, "prefix") + text, args));
	}

	public void actionBar(Player to, String key, Arg... args) {
		String text = raw(to, key);
		if (!text.isEmpty()) to.sendActionBar(parse(text, args));
	}

	private Component parse(String input, Arg... args) {
		TagResolver.Builder resolvers = TagResolver.builder();
		for (Arg arg : args) {
			if (arg.component != null) resolvers.resolver(Placeholder.component(arg.name, arg.component));
			else if (arg.markup) resolvers.resolver(Placeholder.parsed(arg.name, arg.text));
			else resolvers.resolver(Placeholder.unparsed(arg.name, arg.text));
		}
		return mini.deserialize(input, resolvers.build());
	}

	private static final class Lang {
		final Locale locale;
		final YamlConfiguration own;
		final YamlConfiguration defaults;
		final YamlConfiguration english;

		Lang(Locale locale, YamlConfiguration own, YamlConfiguration defaults, YamlConfiguration english) {
			this.locale = locale;
			this.own = own;
			this.defaults = defaults;
			this.english = english;
		}

		String raw(String key) {
			for (YamlConfiguration source : List.of(own, defaults, english)) {
				if (source.isString(key)) return source.getString(key);
			}
			return key;
		}

		List<String> list(String key) {
			for (YamlConfiguration source : List.of(own, defaults, english)) {
				if (source.isList(key)) return source.getStringList(key);
			}
			return List.of();
		}
	}
}
