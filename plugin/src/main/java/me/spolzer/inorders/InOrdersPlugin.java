package me.spolzer.inorders;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import me.spolzer.inorders.api.InOrders;
import me.spolzer.inorders.catalog.Catalog;
import me.spolzer.inorders.catalog.ItemLabels;
import me.spolzer.inorders.catalog.Names;
import me.spolzer.inorders.command.OrdersCommand;
import me.spolzer.inorders.config.Settings;
import me.spolzer.inorders.currency.Currencies;
import me.spolzer.inorders.dialog.Dialogs;
import me.spolzer.inorders.input.Clients;
import me.spolzer.inorders.input.TextInput;
import me.spolzer.inorders.listener.MenuListener;
import me.spolzer.inorders.listener.PlayerListener;
import me.spolzer.inorders.menu.Menu;
import me.spolzer.inorders.menu.OrderLines;
import me.spolzer.inorders.menu.Screens;
import me.spolzer.inorders.menu.Sounds;
import me.spolzer.inorders.order.OrderManager;
import me.spolzer.inorders.permission.OrderLimits;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.storage.OrderStorage;
import me.spolzer.inorders.text.Formats;
import me.spolzer.inorders.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class InOrdersPlugin extends JavaPlugin {
	private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

	private final Currencies currencies = new Currencies();
	private final Messages messages = new Messages(this);
	private final Formats formats = new Formats(messages, currencies);
	private final Catalog catalog = new Catalog();
	private final Clients clients = new Clients();
	private final Names names = new Names();
	private final ItemLabels itemLabels = new ItemLabels(clients);
	private final Dialogs dialogs = new Dialogs(this);
	private final Sounds sounds = new Sounds(this);
	private final MainThread mainThread = new MainThread(this);
	private final OrderLimits limits = new OrderLimits();
	// File reads outside the storage: jar tables at startup, config and language files on reload
	private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
	private Settings settings;
	private OrderStorage storage;
	private OrderManager orders;
	private OrderLines orderLines;
	private Screens screens;
	private TextInput input;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		settings = new Settings(getConfig(), getLogger());
		messages.load(settings.language, settings.defaultLanguage);
		applyPermissionDefaults();
		limits.hook(getLogger());

		storage = new OrderStorage(settings.database, new File(getDataFolder(), "orders.db"), getLogger());
		try {
			storage.open();
		} catch (SQLException | RuntimeException e) {
			String where = settings.database.mysql ? "the MySQL database" : "orders.db";
			getLogger().log(Level.SEVERE, "Could not open " + where + ", disabling InOrders", e);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		orders = new OrderManager(this::settings, storage, currencies, messages, formats, names, sounds, limits, mainThread, getLogger());
		orderLines = new OrderLines(this);
		screens = new Screens(this);
		input = new TextInput(this);

		getServer().getPluginManager().registerEvents(new MenuListener(), this);
		getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
		getServer().getPluginManager().registerEvents(input, this);
		PluginCommand command = getCommand("orders");
		OrdersCommand executor = new OrdersCommand(this);
		command.setExecutor(executor);
		command.setTabCompleter(executor);
		getServer().getServicesManager().register(InOrders.class, new OrderService(this), this, ServicePriority.Normal);

		// The name and icon tables are read from the jar in the background. Economy plugins register with Vault
		// in their own onEnable and the creative tabs need loaded worlds, so the rest waits for the first tick
		CompletableFuture<Void> tables = CompletableFuture.runAsync(() -> {
			names.load(this, getLogger());
			itemLabels.load(this, getLogger());
		}, background);
		Bukkit.getScheduler().runTask(this, () -> {
			currencies.load(settings, messages.locale(), getLogger());
			clients.hook(getLogger());
			tables.thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
				catalog.load(settings, names, getLogger());
				getLogger().info("Item catalog: " + catalog.tabs().size() + " tabs");
			}));
		});

		Bukkit.getScheduler().runTaskTimer(this, () -> input.tick(System.currentTimeMillis()), 20L, 20L);
		Bukkit.getScheduler().runTaskTimer(this, () -> storage.expire(System.currentTimeMillis()), 20L * 5, 20L * 60);
	}

	@Override
	public void onDisable() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) player.closeInventory();
		}
		if (storage != null) {
			// Payments and item returns of operations the database has already saved run here
			if (!mainThread.finish(storage::hasPendingWork, SHUTDOWN_WAIT)) {
				getLogger().warning("The database did not answer within " + SHUTDOWN_WAIT.toSeconds()
						+ " seconds, some order operations were left unfinished");
			}
			storage.close();
		}
		background.shutdown();
	}

	public CompletableFuture<Void> reload() {
		CompletableFuture<Void> done = new CompletableFuture<>();
		CompletableFuture.supplyAsync(() -> {
			Settings fresh = new Settings(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml")), getLogger());
			messages.load(fresh.language, fresh.defaultLanguage);
			return fresh;
		}, background).whenComplete((fresh, error) -> Bukkit.getScheduler().runTask(this, () -> {
			if (error != null) {
				getLogger().log(Level.SEVERE, "Could not reload the configuration", error);
				done.completeExceptionally(error);
				return;
			}
			settings = fresh;
			currencies.load(settings, messages.locale(), getLogger());
			catalog.load(settings, names, getLogger());
			applyPermissionDefaults();
			done.complete(null);
		}));
		return done;
	}

	private void applyPermissionDefaults() {
		setDefault(Permissions.PLAYER, settings.openToEveryone ? PermissionDefault.TRUE : PermissionDefault.FALSE);
	}

	private void setDefault(String name, PermissionDefault value) {
		Permission permission = getServer().getPluginManager().getPermission(name);
		if (permission == null) return;
		if (permission.getDefault() != value) permission.setDefault(value);
		for (String child : permission.getChildren().keySet()) setDefault(child, value);
	}

	public Settings settings() { return settings; }
	public Messages messages() { return messages; }
	public Formats formats() { return formats; }
	public Currencies currencies() { return currencies; }
	public Catalog catalog() { return catalog; }
	public Names names() { return names; }
	public ItemLabels itemLabels() { return itemLabels; }
	public Clients clients() { return clients; }
	public Dialogs dialogs() { return dialogs; }
	public Sounds sounds() { return sounds; }
	public OrderStorage storage() { return storage; }
	public OrderManager orders() { return orders; }
	public OrderLines orderLines() { return orderLines; }
	public Screens screens() { return screens; }
	public TextInput input() { return input; }
}
