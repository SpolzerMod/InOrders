package me.spolzer.inorders.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * How many open orders a player may have. The first that applies wins: inorders.limit.unlimited,
 * the LuckPerms meta key {@value #META_KEY}, the highest inorders.limit.&lt;number&gt;, orders.max-active.
 * A permission can set the limit below the config value, so a default group may get fewer slots.
 */
public final class OrderLimits {
	public static final int UNLIMITED = Integer.MAX_VALUE;
	public static final String META_KEY = "inorders-limit";

	private volatile Function<Player, OptionalInt> meta = player -> OptionalInt.empty();

	public void hook(Logger logger) {
		if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return;
		meta = new LuckPermsMeta(META_KEY);
		logger.info("LuckPerms found, order limits can also be set with the meta key " + META_KEY);
	}

	public int limit(Player player, int fallback) {
		if (player.hasPermission(Permissions.UNLIMITED)) return UNLIMITED;
		OptionalInt fromMeta = meta.apply(player);
		if (fromMeta.isPresent()) return Math.max(0, fromMeta.getAsInt());
		List<String> granted = new ArrayList<>();
		for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
			if (permission.getValue()) granted.add(permission.getPermission());
		}
		return highest(granted).orElse(fallback);
	}

	static OptionalInt highest(Iterable<String> granted) {
		int highest = -1;
		for (String name : granted) {
			if (!name.startsWith(Permissions.LIMIT)) continue;
			try {
				highest = Math.max(highest, Integer.parseInt(name.substring(Permissions.LIMIT.length())));
			} catch (NumberFormatException ignored) {
				// inorders.limit.unlimited and typos
			}
		}
		return highest >= 0 ? OptionalInt.of(highest) : OptionalInt.empty();
	}
}
