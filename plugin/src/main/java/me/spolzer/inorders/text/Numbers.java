package me.spolzer.inorders.text;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Numbers {
	// No signs and no exponent: BigDecimal would accept 1e999999999 and then build a number that size
	private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d*)?|\\.\\d+");

	private Numbers() {}

	/** Reads 1500, 2,5, 2.5k, 1m or 1б. Returns null when the text is not a non-negative number. */
	public static BigDecimal parse(String text) {
		String value = text.trim().toLowerCase(Locale.ROOT).replace(",", ".").replace(" ", "").replace("_", "");
		if (value.isEmpty()) return null;
		long multiplier = switch (value.charAt(value.length() - 1)) {
			case 'k', 'к' -> 1_000L;
			case 'm', 'м' -> 1_000_000L;
			case 'b', 'б' -> 1_000_000_000L;
			default -> 1;
		};
		if (multiplier != 1) value = value.substring(0, value.length() - 1);
		if (!DECIMAL.matcher(value).matches()) return null;
		return new BigDecimal(value).multiply(BigDecimal.valueOf(multiplier));
	}
}
