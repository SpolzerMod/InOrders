package me.spolzer.inorders.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import me.spolzer.inorders.text.Numbers;
import org.bukkit.inventory.ItemStack;

public final class Draft {
	private static final List<String> STACK_SUFFIXES = List.of("stacks", "stack", "st", "s", "стаков", "стака", "стак", "ст", "с");

	private final ItemStack item;
	private int amount;
	private long price = -1;
	private String currency;
	private int days;

	public Draft(ItemStack item, String currency, int days) {
		this.item = item;
		this.amount = item.getMaxStackSize();
		this.currency = currency;
		this.days = days;
	}

	public ItemStack item() { return item.clone(); }
	public int amount() { return amount; }
	public long price() { return price; }
	public String currency() { return currency; }
	public int days() { return days; }
	public boolean hasPrice() { return price > 0; }

	public Draft amount(int value) { amount = value; return this; }
	public Draft price(long value) { price = value; return this; }
	public Draft currency(String value) { currency = value; return this; }
	public Draft days(int value) { days = value; return this; }

	/** Accepts 640, 10st or 10ст for stacks, 2.5k. Returns -1 for invalid input. */
	public static int parseAmount(String text, int stack) {
		String value = text.trim().toLowerCase(Locale.ROOT).replace(" ", "");
		for (String suffix : STACK_SUFFIXES) {
			if (value.endsWith(suffix) && value.length() > suffix.length()) {
				BigDecimal stacks = Numbers.parse(value.substring(0, value.length() - suffix.length()));
				return stacks == null ? -1 : count(stacks.multiply(BigDecimal.valueOf(stack)).setScale(0, RoundingMode.HALF_UP));
			}
		}
		BigDecimal amount = Numbers.parse(value);
		return amount == null ? -1 : count(amount.setScale(0, RoundingMode.DOWN));
	}

	private static int count(BigDecimal value) {
		if (value.signum() <= 0) return -1;
		return value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0 ? Integer.MAX_VALUE : value.intValue();
	}
}
