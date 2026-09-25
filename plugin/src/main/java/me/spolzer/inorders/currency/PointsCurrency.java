package me.spolzer.inorders.currency;

import java.text.NumberFormat;
import java.util.Locale;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;

final class PointsCurrency implements Currency {
	private final PlayerPointsAPI api = PlayerPoints.getInstance().getAPI();
	private final NumberFormat format;

	PointsCurrency(Locale locale) {
		format = NumberFormat.getIntegerInstance(locale);
	}

	@Override public String id() { return "playerpoints"; }
	@Override public Material icon() { return Material.AMETHYST_SHARD; }
	@Override public boolean fractional() { return false; }
	@Override public long balance(Player player) { return Money.ofUnits(api.look(player.getUniqueId())); }

	@Override
	public boolean withdraw(Player player, long amount) {
		int points = points(amount);
		return api.look(player.getUniqueId()) >= points && api.take(player.getUniqueId(), points);
	}

	@Override public boolean deposit(Player player, long amount) { return api.give(player.getUniqueId(), points(amount)); }
	@Override public String format(long amount) { return format.format(Money.units(amount)); }

	// Amounts in this currency are already whole, Money.round takes care of that
	private static int points(long amount) {
		return Math.toIntExact(Money.units(amount));
	}
}
