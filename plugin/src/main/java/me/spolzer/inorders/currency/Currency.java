package me.spolzer.inorders.currency;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Amounts are hundredths, see {@link Money}. */
public interface Currency {
	String id();
	Material icon();
	boolean fractional();
	long balance(Player player);
	boolean withdraw(Player player, long amount);
	boolean deposit(Player player, long amount);
	String format(long amount);
}
