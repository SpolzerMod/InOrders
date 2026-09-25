package me.spolzer.inorders.input;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import java.util.logging.Logger;
import me.spolzer.inorders.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Clients {
	// 1.21.6, the first version with dialogs
	private static final int DIALOG_PROTOCOL = 771;

	private FloodgateBridge floodgate;
	private MethodHandle viaVersion;
	private Object via;

	public void hook(Logger logger) {
		if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
			floodgate = new FloodgateBridge();
			logger.info("Floodgate found, Bedrock players will get chest menus and native forms");
		}
		if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
			try {
				via = Class.forName("com.viaversion.viaversion.api.Via").getMethod("getAPI").invoke(null);
				viaVersion = MethodHandles.publicLookup().findVirtual(Class.forName("com.viaversion.viaversion.api.ViaAPI"), "getPlayerVersion",
						MethodType.methodType(int.class, UUID.class));
				logger.info("ViaVersion found, players on clients older than 1.21.6 will get chest menus");
			} catch (ReflectiveOperationException | RuntimeException e) {
				logger.warning("Could not hook into ViaVersion, all Java players will get dialogs: " + e);
			}
		}
	}

	public boolean isBedrock(Player player) {
		return floodgate != null && floodgate.isBedrock(player);
	}

	FloodgateBridge floodgate() { return floodgate; }

	public boolean supportsDialogs(Player player) {
		return !isBedrock(player) && protocolAtLeast(player, DIALOG_PROTOCOL);
	}

	/** Without ViaVersion every Java client runs the server's own version. */
	public boolean protocolAtLeast(Player player, int protocol) {
		if (isBedrock(player)) return false;
		if (viaVersion == null) return true;
		try {
			int version = (int) viaVersion.invoke(via, player.getUniqueId());
			return version < 0 || version >= protocol;
		} catch (Throwable e) {
			return true;
		}
	}

	public boolean usesDialogs(Player player, Settings settings) {
		return settings.forms != Settings.FormMode.CHEST && supportsDialogs(player);
	}
}
