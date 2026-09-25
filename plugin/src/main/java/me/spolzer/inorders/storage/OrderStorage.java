package me.spolzer.inorders.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.api.OrderStatus;
import me.spolzer.inorders.config.Settings;
import org.bukkit.inventory.ItemStack;

// Writes go through one thread, so deliveries to the same order never interleave on this server.
// Servers sharing a MySQL database are kept apart by row locks inside the transactions.
public final class OrderStorage {
	private static final List<String> SQLITE_SCHEMA = List.of("""
			CREATE TABLE IF NOT EXISTS inorders_orders (
				id INTEGER PRIMARY KEY AUTOINCREMENT,
				server TEXT NOT NULL,
				owner TEXT NOT NULL,
				owner_name TEXT NOT NULL,
				item BLOB NOT NULL,
				search TEXT NOT NULL,
				amount INTEGER NOT NULL,
				delivered INTEGER NOT NULL DEFAULT 0,
				collected INTEGER NOT NULL DEFAULT 0,
				price_cents INTEGER NOT NULL,
				currency TEXT NOT NULL,
				created INTEGER NOT NULL,
				expires INTEGER NOT NULL,
				status TEXT NOT NULL,
				refunded INTEGER NOT NULL DEFAULT 0
			)""",
			"CREATE INDEX IF NOT EXISTS inorders_orders_open ON inorders_orders (status, expires)",
			"CREATE INDEX IF NOT EXISTS inorders_orders_owner ON inorders_orders (owner)");

	private static final List<String> MYSQL_SCHEMA = List.of("""
			CREATE TABLE IF NOT EXISTS inorders_orders (
				id BIGINT AUTO_INCREMENT PRIMARY KEY,
				server VARCHAR(64) NOT NULL,
				owner CHAR(36) NOT NULL,
				owner_name VARCHAR(64) NOT NULL,
				item MEDIUMBLOB NOT NULL,
				search VARCHAR(512) NOT NULL,
				amount INT NOT NULL,
				delivered INT NOT NULL DEFAULT 0,
				collected INT NOT NULL DEFAULT 0,
				price_cents BIGINT NOT NULL,
				currency VARCHAR(32) NOT NULL,
				created BIGINT NOT NULL,
				expires BIGINT NOT NULL,
				status VARCHAR(16) NOT NULL,
				refunded TINYINT NOT NULL DEFAULT 0,
				INDEX inorders_orders_open (status, expires),
				INDEX inorders_orders_owner (owner)
			)""");

	private final Settings.Database settings;
	private final File file;
	private final Logger logger;
	private final ItemCodec items;
	private final String server;
	private final String lock;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "InOrders-Storage");
		thread.setDaemon(true);
		return thread;
	});
	// Tasks queued or running on the executors, see hasPendingWork()
	private final AtomicInteger pending = new AtomicInteger();
	private ExecutorService reader;
	private HikariDataSource pool;

	public OrderStorage(Settings.Database settings, File file, Logger logger) {
		this(settings, file, logger, ItemCodec.PAPER);
	}

	public OrderStorage(Settings.Database settings, File file, Logger logger, ItemCodec items) {
		this.settings = settings;
		this.file = file;
		this.logger = logger;
		this.items = items;
		this.server = settings.serverId;
		this.lock = settings.mysql ? " FOR UPDATE" : "";
	}

	public void open() throws SQLException {
		HikariConfig config = new HikariConfig();
		config.setPoolName("InOrders");
		if (settings.mysql) {
			config.setJdbcUrl("jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.name);
			config.setDriverClassName("com.mysql.cj.jdbc.Driver");
			config.setUsername(settings.user);
			config.setPassword(settings.password);
			config.setMaximumPoolSize(settings.poolSize);
			settings.properties.forEach(config::addDataSourceProperty);
			reader = Executors.newVirtualThreadPerTaskExecutor();
		} else {
			config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
			config.setDriverClassName("org.sqlite.JDBC");
			config.setMaximumPoolSize(1);
			reader = writer;
		}
		pool = new HikariDataSource(config);

		try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
			if (!settings.mysql) st.execute("PRAGMA journal_mode=WAL");
			for (String sql : settings.mysql ? MYSQL_SCHEMA : SQLITE_SCHEMA) st.execute(sql);
			migratePrices(c);
		}
	}

	// Early builds kept prices as REAL/DOUBLE in a "price" column. Each step is safe to repeat after a crash
	private void migratePrices(Connection c) throws SQLException {
		Set<String> columns = columns(c);
		if (!columns.contains("price")) return;
		try (Statement st = c.createStatement()) {
			if (!columns.contains("price_cents")) {
				st.execute("ALTER TABLE inorders_orders ADD COLUMN price_cents BIGINT NOT NULL DEFAULT 0");
			}
			int rows = st.executeUpdate("UPDATE inorders_orders SET price_cents = ROUND(price * 100)");
			st.execute("ALTER TABLE inorders_orders DROP COLUMN price");
			logger.info("Converted the prices of " + rows + " order(s) to hundredths");
		}
	}

	private static Set<String> columns(Connection c) throws SQLException {
		Set<String> names = new HashSet<>();
		try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM inorders_orders WHERE 1 = 0")) {
			ResultSetMetaData meta = rs.getMetaData();
			for (int i = 1; i <= meta.getColumnCount(); i++) names.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));
		}
		return names;
	}

	/**
	 * True while a query is queued or running. The counter drops after complete(), so when this turns false
	 * the callbacks of every query are already on their way.
	 */
	public boolean hasPendingWork() {
		return pending.get() > 0;
	}

	public void close() {
		writer.shutdown();
		if (reader != null && reader != writer) reader.shutdown();
		try {
			if (!writer.awaitTermination(10, TimeUnit.SECONDS)) logger.warning("Timed out waiting for pending database writes");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (pool != null) pool.close();
	}

	public CompletableFuture<Long> create(NewOrder order) {
		byte[] item = items.encode(order.item());
		return query(writer, "Could not save a new order of " + order.ownerName(), c -> {
			try (PreparedStatement ps = c.prepareStatement("""
					INSERT INTO inorders_orders (server, owner, owner_name, item, search, amount, price_cents, currency, created, expires, status)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
				ps.setString(1, server);
				ps.setString(2, order.owner().toString());
				ps.setString(3, order.ownerName());
				ps.setBytes(4, item);
				ps.setString(5, order.search());
				ps.setInt(6, order.amount());
				ps.setLong(7, order.price());
				ps.setString(8, order.currency());
				ps.setLong(9, order.created());
				ps.setLong(10, order.expires());
				ps.setString(11, OrderStatus.ACTIVE.name());
				ps.executeUpdate();
				try (ResultSet keys = ps.getGeneratedKeys()) {
					if (!keys.next()) throw new SQLException("No id returned for the new order");
					return keys.getLong(1);
				}
			}
		});
	}

	/** Page of open orders, one extra row tells whether there is a next page. */
	public CompletableFuture<List<Order>> browse(OrderQuery filter, long now, int offset, int limit) {
		StringBuilder sql = new StringBuilder("SELECT * FROM inorders_orders WHERE status = ? AND expires > ?");
		List<Object> params = new ArrayList<>(List.of(OrderStatus.ACTIVE.name(), now));
		if (!settings.shared) {
			sql.append(" AND server = ?");
			params.add(server);
		}
		if (filter.currency() != null) {
			sql.append(" AND currency = ?");
			params.add(filter.currency());
		}
		if (filter.search() != null) {
			sql.append(" AND search LIKE ? ESCAPE '!'");
			params.add("%" + filter.search().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
		}
		if (filter.itemKey() != null) {
			sql.append(" AND search = ?");
			params.add(filter.itemKey());
		}
		sql.append(" ORDER BY ").append(filter.sort().sql).append(" LIMIT ? OFFSET ?");
		params.add(limit);
		params.add(offset);
		return query(reader, "Could not load the order list", c -> {
			try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
				for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
				return readAll(ps);
			}
		});
	}

	public CompletableFuture<List<Order>> ordersOf(UUID owner) {
		String sql = "SELECT * FROM inorders_orders WHERE owner = ?" + (settings.shared ? "" : " AND server = ?") + " ORDER BY created DESC";
		return query(reader, "Could not load the orders of " + owner, c -> {
			try (PreparedStatement ps = c.prepareStatement(sql)) {
				ps.setString(1, owner.toString());
				if (!settings.shared) ps.setString(2, server);
				return readAll(ps);
			}
		});
	}

	public CompletableFuture<Optional<Order>> order(long id) {
		return query(reader, "Could not load order #" + id, c -> Optional.ofNullable(select(c, id, false)));
	}

	public CompletableFuture<Integer> countOpen(UUID owner, long now) {
		return query(reader, "Could not count the orders of " + owner, c -> {
			try (PreparedStatement ps = c.prepareStatement(
					"SELECT COUNT(*) FROM inorders_orders WHERE owner = ? AND status = ? AND expires > ?")) {
				ps.setString(1, owner.toString());
				ps.setString(2, OrderStatus.ACTIVE.name());
				ps.setLong(3, now);
				try (ResultSet rs = ps.executeQuery()) {
					return rs.next() ? rs.getInt(1) : 0;
				}
			}
		});
	}

	public CompletableFuture<Delivery> deliver(long id, int count, long now) {
		return query(writer, "Could not save a delivery to order #" + id, c -> transaction(c, () -> {
			Order order = select(c, id, true);
			if (order == null || !order.isOpen(now)) return Delivery.NONE;
			int accepted = Math.min(count, order.remaining());
			if (accepted <= 0) return Delivery.NONE;
			boolean filled = accepted == order.remaining();
			try (PreparedStatement ps = c.prepareStatement("UPDATE inorders_orders SET delivered = ?, status = ? WHERE id = ?")) {
				ps.setInt(1, order.delivered() + accepted);
				ps.setString(2, (filled ? OrderStatus.FILLED : OrderStatus.ACTIVE).name());
				ps.setLong(3, id);
				ps.executeUpdate();
			}
			return new Delivery(accepted, filled);
		}));
	}

	/**
	 * Used when the seller could not be paid. The order may have been filled by this delivery, so it is reopened.
	 * Fails when the owner has already collected these items, then they must not go back to the seller.
	 */
	public CompletableFuture<Boolean> revertDelivery(long id, int count) {
		return query(writer, "Could not revert a delivery of " + count + " items to order #" + id, c -> {
			try (PreparedStatement ps = c.prepareStatement("""
					UPDATE inorders_orders SET delivered = delivered - ?,
					status = CASE WHEN status = 'FILLED' THEN 'ACTIVE' ELSE status END
					WHERE id = ? AND delivered - ? >= collected""")) {
				ps.setInt(1, count);
				ps.setLong(2, id);
				ps.setInt(3, count);
				return ps.executeUpdate() == 1;
			}
		});
	}

	public CompletableFuture<Optional<Order>> cancel(long id, long now) {
		return query(writer, "Could not cancel order #" + id, c -> transaction(c, () -> {
			Order order = select(c, id, true);
			if (order == null || !order.isOpen(now)) return Optional.<Order>empty();
			setStatus(c, id, OrderStatus.CANCELLED);
			return Optional.of(order);
		}));
	}

	/**
	 * Reserves up to {@code maxItems} delivered items and, for closed orders, the unspent payment.
	 * The caller hands them out and then calls {@link #settle(long)}, or {@link #unclaim} if that failed.
	 */
	public CompletableFuture<Claim> claim(long id, UUID owner, int maxItems, long now) {
		return query(writer, "Could not claim order #" + id, c -> transaction(c, () -> {
			Order order = select(c, id, true);
			if (order == null || !order.owner().equals(owner)) return null;
			int items = Math.min(maxItems, order.uncollected());
			OrderStatus status = order.status(now);
			boolean closed = status == OrderStatus.CANCELLED || status == OrderStatus.EXPIRED;
			int refund = closed && !order.refunded() ? order.remaining() : 0;
			try (PreparedStatement ps = c.prepareStatement(
					"UPDATE inorders_orders SET collected = collected + ?, refunded = ?, status = ? WHERE id = ?")) {
				ps.setInt(1, items);
				ps.setInt(2, order.refunded() || closed ? 1 : 0);
				ps.setString(3, status.name());
				ps.setLong(4, id);
				ps.executeUpdate();
			}
			return new Claim(order, items, refund);
		}));
	}

	public void unclaim(long id, int items, boolean refund) {
		write("Could not return a failed claim to order #" + id, c -> {
			try (PreparedStatement ps = c.prepareStatement(
					"UPDATE inorders_orders SET collected = collected - ?, refunded = CASE WHEN ? THEN 0 ELSE refunded END WHERE id = ?")) {
				ps.setInt(1, items);
				ps.setBoolean(2, refund);
				ps.setLong(3, id);
				ps.executeUpdate();
			}
		});
	}

	public CompletableFuture<Boolean> settle(long id) {
		return query(writer, "Could not remove finished order #" + id, c -> {
			try (PreparedStatement ps = c.prepareStatement(
					"DELETE FROM inorders_orders WHERE id = ? AND status <> ? AND collected = delivered AND (refunded = 1 OR delivered = amount)")) {
				ps.setLong(1, id);
				ps.setString(2, OrderStatus.ACTIVE.name());
				return ps.executeUpdate() > 0;
			}
		});
	}

	public void expire(long now) {
		write("Could not mark expired orders", c -> {
			try (PreparedStatement ps = c.prepareStatement("UPDATE inorders_orders SET status = ? WHERE status = ? AND expires <= ?")) {
				ps.setString(1, OrderStatus.EXPIRED.name());
				ps.setString(2, OrderStatus.ACTIVE.name());
				ps.setLong(3, now);
				int expired = ps.executeUpdate();
				if (expired > 0) logger.info(expired + " order(s) expired");
			}
		});
	}

	private void setStatus(Connection c, long id, OrderStatus status) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("UPDATE inorders_orders SET status = ? WHERE id = ?")) {
			ps.setString(1, status.name());
			ps.setLong(2, id);
			ps.executeUpdate();
		}
	}

	private Order select(Connection c, long id, boolean forUpdate) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("SELECT * FROM inorders_orders WHERE id = ?" + (forUpdate ? lock : ""))) {
			ps.setLong(1, id);
			List<Order> found = readAll(ps);
			return found.isEmpty() ? null : found.getFirst();
		}
	}

	private List<Order> readAll(PreparedStatement ps) throws SQLException {
		List<Order> orders = new ArrayList<>();
		try (ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				ItemStack item;
				try {
					item = items.decode(rs.getBytes("item"));
				} catch (RuntimeException e) {
					// Created on a newer Minecraft version sharing this database
					logger.log(Level.FINE, "Skipped order #" + rs.getLong("id") + " with an unreadable item", e);
					continue;
				}
				orders.add(new Order(rs.getLong("id"), UUID.fromString(rs.getString("owner")), rs.getString("owner_name"), item,
						rs.getInt("amount"), rs.getInt("delivered"), rs.getInt("collected"), rs.getLong("price_cents"),
						rs.getString("currency"), rs.getLong("created"), rs.getLong("expires"),
						OrderStatus.valueOf(rs.getString("status")), rs.getInt("refunded") != 0));
			}
		}
		return orders;
	}

	private static <T> T transaction(Connection c, Work<T> work) throws SQLException {
		c.setAutoCommit(false);
		try {
			T result = work.run();
			c.commit();
			return result;
		} catch (SQLException | RuntimeException e) {
			c.rollback();
			throw e;
		} finally {
			c.setAutoCommit(true);
		}
	}

	private void write(String failure, Update update) {
		boolean queued = submit(writer, () -> {
			try (Connection c = pool.getConnection()) {
				update.run(c);
			} catch (Exception e) {
				logger.log(Level.SEVERE, failure, e);
			}
		});
		if (!queued) logger.severe(failure + ": the storage is already closed");
	}

	private <T> CompletableFuture<T> query(ExecutorService on, String failure, Query<T> query) {
		CompletableFuture<T> future = new CompletableFuture<>();
		boolean queued = submit(on, () -> {
			try (Connection c = pool.getConnection()) {
				future.complete(query.run(c));
			} catch (Exception e) {
				logger.log(Level.SEVERE, failure, e);
				future.completeExceptionally(e);
			}
		});
		if (!queued) {
			logger.severe(failure + ": the storage is already closed");
			future.completeExceptionally(new RejectedExecutionException("The storage is closed"));
		}
		return future;
	}

	private boolean submit(ExecutorService on, Runnable task) {
		pending.incrementAndGet();
		try {
			on.execute(() -> {
				try {
					task.run();
				} finally {
					pending.decrementAndGet();
				}
			});
			return true;
		} catch (RejectedExecutionException e) {
			pending.decrementAndGet();
			return false;
		}
	}

	private interface Work<T> { T run() throws SQLException; }
	private interface Update { void run(Connection c) throws SQLException; }
	private interface Query<T> { T run(Connection c) throws SQLException; }
}
