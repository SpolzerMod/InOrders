package me.spolzer.inorders.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import me.spolzer.inorders.text.Numbers;

/**
 * Money is a long of hundredths for every currency. Whole-unit currencies like PlayerPoints keep multiples
 * of 100, so a stored price means the same thing even after the economy plugin is replaced.
 * Doubles are used only at the Vault boundary.
 */
public final class Money {
	private static final int SCALE = 2;
	private static final long UNIT = 100;
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal MAX = BigDecimal.valueOf(Long.MAX_VALUE);
	private static final double MAX_UNITS = (double) Long.MAX_VALUE / UNIT;

	private Money() {}

	public static long ofUnits(long units) {
		return Math.multiplyExact(units, UNIT);
	}

	public static long units(long amount) {
		return amount / UNIT;
	}

	/** For config values and Vault balances, rounded down to a hundredth. */
	public static long of(double value) {
		if (Double.isNaN(value)) return 0;
		if (value >= MAX_UNITS) return Long.MAX_VALUE;
		if (value <= -MAX_UNITS) return Long.MIN_VALUE;
		return BigDecimal.valueOf(value).movePointRight(SCALE).setScale(0, RoundingMode.FLOOR).longValue();
	}

	public static double toDouble(long amount) {
		return (double) amount / UNIT;
	}

	public static BigDecimal toDecimal(long amount) {
		return BigDecimal.valueOf(amount, SCALE);
	}

	/** Drops the hundredths a whole-unit currency cannot hold. */
	public static long round(long amount, boolean fractional) {
		return fractional ? amount : amount - Math.floorMod(amount, UNIT);
	}

	// Saturates instead of wrapping around, an absurd total then simply fails the balance check
	public static long multiply(long price, int count) {
		try {
			return Math.multiplyExact(price, count);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	public static long add(long a, long b) {
		try {
			return Math.addExact(a, b);
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	/** The given percent of the amount, rounded down. */
	public static long percent(long amount, double percent, boolean fractional) {
		return share(amount, BigDecimal.valueOf(percent), fractional);
	}

	/** What is left after the given percent is taken. Rounded down too, so the seller never gets a hundredth extra. */
	public static long withoutPercent(long amount, double percent, boolean fractional) {
		return share(amount, HUNDRED.subtract(BigDecimal.valueOf(percent)), fractional);
	}

	private static long share(long amount, BigDecimal percent, boolean fractional) {
		BigDecimal share = BigDecimal.valueOf(amount).multiply(percent).divide(HUNDRED, 0, RoundingMode.DOWN);
		return round(share.longValueExact(), fractional);
	}

	/** Parses what a player typed: 1500, 2.5k, 1m. Returns -1 when it is not a number. */
	public static long parse(String text, boolean fractional) {
		BigDecimal value = Numbers.parse(text);
		if (value == null) return -1;
		BigDecimal hundredths = value.movePointRight(SCALE).setScale(0, RoundingMode.DOWN);
		if (hundredths.compareTo(MAX) > 0) return -1;
		return round(hundredths.longValue(), fractional);
	}

	/** The amount as a player would type it: 3 or 2.50. */
	public static String plain(long amount) {
		return amount % UNIT == 0 ? Long.toString(amount / UNIT) : toDecimal(amount).toPlainString();
	}
}
