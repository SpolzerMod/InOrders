package me.spolzer.inorders.input;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.spolzer.inorders.InOrdersPlugin;
import me.spolzer.inorders.dialog.Dialogs;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Messages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * One line of text from a player: a dialog on modern clients, a native form on Bedrock,
 * the chat for everything else. The answer is null when the player cancels.
 */
public final class TextInput implements Listener {
	private static final long CHAT_TIMEOUT = 60_000;

	private final InOrdersPlugin plugin;
	private final Map<UUID, Pending> chat = new ConcurrentHashMap<>();

	private record Pending(Consumer<String> answer, long until) {}

	public TextInput(InOrdersPlugin plugin) {
		this.plugin = plugin;
	}

	public void ask(Player player, String key, String initial, Consumer<String> answer, Arg... args) {
		Messages messages = plugin.messages();
		Clients clients = plugin.clients();
		if (clients.usesDialogs(player, plugin.settings())) {
			Dialogs dialogs = plugin.dialogs();
			dialogs.show(player, messages.get(player, key + ".title", args), Dialogs.body(null, messages.lore(player, key + ".hint", args)),
					List.of(DialogInput.text("value", messages.get(player, key + ".label", args))
							.initial(initial)
							.maxLength(64)
							.width(Dialogs.WIDTH)
							.build()),
					DialogType.confirmation(
							dialogs.button(messages.get(player, "input.confirm"), null, view -> answer.accept(view.getText("value"))),
							dialogs.button(messages.get(player, "input.cancel"), () -> answer.accept(null))));
			return;
		}
		if (clients.isBedrock(player)) {
			clients.floodgate().askText(player, messages.plain(player, key + ".title", args), messages.plain(player, key + ".label", args),
					initial, text -> Bukkit.getScheduler().runTask(plugin, () -> {
						if (player.isOnline()) answer.accept(text);
					}));
			return;
		}
		player.closeInventory();
		chat.put(player.getUniqueId(), new Pending(answer, System.currentTimeMillis() + CHAT_TIMEOUT));
		messages.send(player, "input.chat", Arg.of("label", messages.get(player, key + ".label", args)));
	}

	public void tick(long now) {
		chat.entrySet().removeIf(entry -> {
			if (entry.getValue().until > now) return false;
			Player player = Bukkit.getPlayer(entry.getKey());
			if (player != null) plugin.messages().send(player, "input.timeout");
			return true;
		});
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncChatEvent event) {
		Pending pending = chat.remove(event.getPlayer().getUniqueId());
		if (pending == null) return;
		event.setCancelled(true);
		String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!player.isOnline()) return;
			String lower = text.toLowerCase(Locale.ROOT);
			boolean cancel = plugin.messages().list(player, "input.cancel-words").stream()
					.anyMatch(word -> word.trim().toLowerCase(Locale.ROOT).equals(lower));
			pending.answer.accept(cancel ? null : text);
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		chat.remove(event.getPlayer().getUniqueId());
	}
}
