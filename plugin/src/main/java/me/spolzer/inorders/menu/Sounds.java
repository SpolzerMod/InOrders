package me.spolzer.inorders.menu;

import me.spolzer.inorders.InOrdersPlugin;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.Player;

public final class Sounds {
	private final InOrdersPlugin plugin;

	public Sounds(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	public void play(Player player, String name) {
		Sound sound = plugin.settings().sound(name);
		if (sound != null) player.playSound(sound, Sound.Emitter.self());
	}
}
