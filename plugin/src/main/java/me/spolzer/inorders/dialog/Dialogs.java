package me.spolzer.inorders.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Every dialog is one centered column: the item, a short text block, inputs, and two buttons in the footer.
 * Everything uses the same width, so the edges line up.
 */
public final class Dialogs {
	public static final int WIDTH = 300;
	public static final int HALF = 148;
	private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
			.uses(1)
			.lifetime(Duration.ofMinutes(15))
			.build();
	private static final MethodHandle CLOSE_DIALOG = closeDialog();

	private final Plugin plugin;

	public Dialogs(Plugin plugin) {
		this.plugin = plugin;
	}

	public ActionButton button(Component label, Component tooltip, Consumer<DialogResponseView> action) {
		return button(label, tooltip, HALF, action);
	}

	public ActionButton button(Component label, Component tooltip, int width, Consumer<DialogResponseView> action) {
		return ActionButton.create(label, tooltip, width,
				DialogAction.customClick((view, audience) -> onMain(() -> action.accept(view)), ONCE));
	}

	public ActionButton button(Component label, Runnable action) {
		return button(label, null, view -> action.run());
	}

	public static List<DialogBody> body(ItemStack item, List<Component> text) {
		return body(item, text, WIDTH);
	}

	public static List<DialogBody> body(ItemStack item, List<Component> text, int width) {
		List<DialogBody> body = new ArrayList<>();
		if (item != null) body.add(DialogBody.item(item).showDecorations(true).showTooltip(true).build());
		if (!text.isEmpty()) body.add(DialogBody.plainMessage(Component.join(JoinConfiguration.newlines(), text), width));
		return body;
	}

	public void show(Player player, Component title, List<DialogBody> body, List<? extends DialogInput> inputs,
			DialogType type) {
		player.closeInventory();
		DialogBase base = DialogBase.builder(title).body(body).inputs(inputs).build();
		player.showDialog(Dialog.create(factory -> factory.empty().base(base).type(type)));
	}

	// closeDialog was added in 1.21.8, on 1.21.7 the dialog stays until a button is pressed
	public void close(Player player) {
		if (CLOSE_DIALOG == null) return;
		try {
			CLOSE_DIALOG.invoke(player);
		} catch (Throwable e) {
			plugin.getLogger().log(Level.FINE, "Could not close a dialog", e);
		}
	}

	private void onMain(Runnable task) {
		if (Bukkit.isPrimaryThread()) task.run();
		else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
	}

	private static MethodHandle closeDialog() {
		try {
			return MethodHandles.publicLookup().findVirtual(Audience.class, "closeDialog", MethodType.methodType(void.class));
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}
}
