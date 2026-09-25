package me.spolzer.inorders.permission;

import me.spolzer.inorders.api.Order;
import org.bukkit.entity.Player;

/** Permission nodes. They are also declared in plugin.yml, so LuckPerms can suggest them in its editor. */
public final class Permissions {
	public static final String PLAYER = "inorders.player";
	public static final String USE = "inorders.use";
	public static final String CREATE = "inorders.create";
	public static final String DELIVER = "inorders.deliver";
	public static final String CANCEL = "inorders.cancel";
	public static final String CURRENCY = "inorders.currency.";
	public static final String LIMIT = "inorders.limit.";
	public static final String UNLIMITED = "inorders.limit.unlimited";
	public static final String NO_FEE = "inorders.bypass.fee";
	public static final String NO_TAX = "inorders.bypass.tax";
	public static final String ADMIN_CANCEL = "inorders.admin.cancel";
	public static final String ADMIN_OPEN = "inorders.admin.open";
	public static final String ADMIN_RELOAD = "inorders.admin.reload";
	public static final String ADMIN_WORLDS = "inorders.admin.worlds";

	private Permissions() {}

	/** Owners need inorders.cancel for their own orders, orders of other players need the staff permission. */
	public static boolean canCancel(Player player, Order order) {
		return player.hasPermission(order.owner().equals(player.getUniqueId()) ? CANCEL : ADMIN_CANCEL);
	}
}
