package me.spolzer.inorders.permission;

import java.util.OptionalInt;
import java.util.function.Function;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

// Loaded only when LuckPerms is installed
final class LuckPermsMeta implements Function<Player, OptionalInt> {
	private final LuckPerms api = LuckPermsProvider.get();
	private final String key;

	LuckPermsMeta(String key) {
		this.key = key;
	}

	@Override
	public OptionalInt apply(Player player) {
		// Cached by LuckPerms for online players and resolved in their current contexts (world, server)
		String value = api.getPlayerAdapter(Player.class).getMetaData(player).getMetaValue(key);
		if (value == null) return OptionalInt.empty();
		try {
			return OptionalInt.of(Integer.parseInt(value.trim()));
		} catch (NumberFormatException e) {
			return OptionalInt.empty();
		}
	}
}
