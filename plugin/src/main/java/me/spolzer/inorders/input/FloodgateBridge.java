package me.spolzer.inorders.input;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

final class FloodgateBridge {
	private final FloodgateApi api = FloodgateApi.getInstance();

	boolean isBedrock(Player player) {
		return api.isFloodgatePlayer(player.getUniqueId());
	}

	void askText(Player player, String title, String label, String initial, Consumer<String> answer) {
		CustomForm.Builder form = CustomForm.builder().title(title)
				.input(label, "", initial)
				.validResultHandler(response -> answer.accept(response.asInput(0)))
				.closedOrInvalidResultHandler(() -> answer.accept(null));
		if (!api.sendForm(player.getUniqueId(), form)) answer.accept(null);
	}
}
