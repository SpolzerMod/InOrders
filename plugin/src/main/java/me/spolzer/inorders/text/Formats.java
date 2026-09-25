package me.spolzer.inorders.text;

import java.util.concurrent.TimeUnit;
import me.spolzer.inorders.currency.Currencies;
import me.spolzer.inorders.currency.Currency;
import me.spolzer.inorders.currency.Money;
import me.spolzer.inorders.permission.OrderLimits;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public final class Formats {
	private static final int BAR_LENGTH = 12;

	private final Messages messages;
	private final Currencies currencies;

	public Formats(Messages messages, Currencies currencies) {
		this.messages = messages;
		this.currencies = currencies;
	}

	public Arg money(CommandSender to, String name, String currencyId, long amount) {
		Currency currency = currencies.byId(currencyId);
		String value = currency != null ? currency.format(amount) : Money.plain(amount);
		String key = plural(to, "format.money." + currencyId, Money.units(amount));
		return Arg.markup(name, messages.markup(to, key, Arg.of("amount", value)));
	}

	public Arg limit(CommandSender to, int limit) {
		if (limit == OrderLimits.UNLIMITED) return Arg.markup("limit", messages.markup(to, "format.unlimited"));
		return Arg.of("limit", limit);
	}

	public Arg timeLeft(CommandSender to, long millis) {
		String text;
		if (millis < TimeUnit.MINUTES.toMillis(1)) {
			text = messages.markup(to, "format.time.soon");
		} else {
			long days = TimeUnit.MILLISECONDS.toDays(millis);
			long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
			long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
			if (days > 0) text = messages.markup(to, "format.time.days", Arg.of("days", days), Arg.of("hours", hours));
			else if (hours > 0) text = messages.markup(to, "format.time.hours", Arg.of("hours", hours), Arg.of("minutes", minutes));
			else text = messages.markup(to, "format.time.minutes", Arg.of("minutes", minutes));
		}
		return Arg.markup("time", text);
	}

	public Arg days(CommandSender to, int days) {
		return Arg.markup("days", messages.markup(to, plural(to, "format.days", days), Arg.of("count", days)));
	}

	public Arg progress(CommandSender to, int done, int total) {
		int filled = total <= 0 ? 0 : (int) Math.round((double) done / total * BAR_LENGTH);
		int percent = total <= 0 ? 0 : (int) Math.floor(done * 100.0 / total);
		String symbol = messages.markup(to, "format.progress.symbol");
		return Arg.markup("progress", messages.markup(to, "format.progress.bar",
				Arg.markup("filled", symbol.repeat(filled)), Arg.markup("empty", symbol.repeat(BAR_LENGTH - filled)),
				Arg.of("percent", percent)));
	}

	/** 640 becomes "640 (10 x 64)" for stackable items. */
	public Arg amount(CommandSender to, String name, int amount, ItemStack item) {
		int stack = item.getMaxStackSize();
		if (stack <= 1 || amount < stack) return Arg.markup(name, messages.markup(to, "format.amount.plain", Arg.of("count", amount)));
		int rest = amount % stack;
		return Arg.markup(name, messages.markup(to, rest == 0 ? "format.amount.stacks" : "format.amount.stacks-rest",
				Arg.of("count", amount), Arg.of("stacks", amount / stack), Arg.of("size", stack), Arg.of("rest", rest)));
	}

	public static Arg item(ItemStack item) {
		return Arg.of("item", item.effectiveName());
	}

	// Keys end in .one, .few and .many. Only Russian-like languages use .few
	private String plural(CommandSender to, String key, long n) {
		String language = messages.locale(to).getLanguage();
		if (language.equals("ru") || language.equals("uk") || language.equals("be")) {
			long last = n % 10, lastTwo = n % 100;
			if (last == 1 && lastTwo != 11) return key + ".one";
			if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) return key + ".few";
			return key + ".many";
		}
		return key + (n == 1 ? ".one" : ".many");
	}
}
