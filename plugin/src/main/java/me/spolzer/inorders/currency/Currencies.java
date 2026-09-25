package me.spolzer.inorders.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import me.spolzer.inorders.config.Settings;
import me.spolzer.inorders.permission.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permissible;

public final class Currencies {
	private volatile List<Currency> active = List.of();

	public void load(Settings settings, Locale locale, Logger logger) {
		List<Currency> found = new ArrayList<>();
		if (settings.currency("money").enabled() && Bukkit.getPluginManager().isPluginEnabled("Vault")) {
			Currency money = VaultCurrency.create();
			if (money != null) found.add(money);
			else logger.warning("Vault is installed, but no economy plugin is registered. Orders for money are disabled");
		}
		if (settings.currency("playerpoints").enabled() && Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
			found.add(new PointsCurrency(locale));
		}
		active = List.copyOf(found);
		List<String> ids = found.stream().map(Currency::id).toList();
		if (ids.isEmpty()) logger.warning("No currency is available. Install Vault with an economy plugin or PlayerPoints");
		else logger.info("Currencies: " + String.join(", ", ids));
	}

	public List<Currency> active() { return active; }

	public List<Currency> allowed(Permissible who) {
		return active.stream().filter(currency -> allowed(who, currency)).toList();
	}

	public boolean allowed(Permissible who, Currency currency) {
		return who.hasPermission(Permissions.CURRENCY + currency.id());
	}

	public Currency byId(String id) {
		for (Currency currency : active) {
			if (currency.id().equals(id)) return currency;
		}
		return null;
	}
}
