package me.spolzer.inorders.order;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.spolzer.inorders.MainThread;
import me.spolzer.inorders.api.Order;
import me.spolzer.inorders.api.event.OrderCancelEvent;
import me.spolzer.inorders.api.event.OrderCreateEvent;
import me.spolzer.inorders.api.event.OrderDeliverEvent;
import me.spolzer.inorders.catalog.Names;
import me.spolzer.inorders.config.Settings;
import me.spolzer.inorders.currency.Currencies;
import me.spolzer.inorders.currency.Currency;
import me.spolzer.inorders.currency.Money;
import me.spolzer.inorders.menu.Sounds;
import me.spolzer.inorders.permission.OrderLimits;
import me.spolzer.inorders.permission.Permissions;
import me.spolzer.inorders.storage.Claim;
import me.spolzer.inorders.storage.Delivery;
import me.spolzer.inorders.storage.NewOrder;
import me.spolzer.inorders.storage.OrderStorage;
import me.spolzer.inorders.text.Arg;
import me.spolzer.inorders.text.Formats;
import me.spolzer.inorders.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class OrderManager {
	private final Supplier<Settings> settings;
	private final OrderStorage storage;
	private final Currencies currencies;
	private final Messages messages;
	private final Formats formats;
	private final Names names;
	private final Sounds sounds;
	private final OrderLimits limits;
	private final MainThread main;
	private final Logger logger;
	private final PlayerLocks locks = new PlayerLocks();

	public record Problem(String key, Arg... args) {}

	public OrderManager(Supplier<Settings> settings, OrderStorage storage, Currencies currencies, Messages messages,
			Formats formats, Names names, Sounds sounds, OrderLimits limits, MainThread main, Logger logger) {
		this.settings = settings;
		this.storage = storage;
		this.currencies = currencies;
		this.messages = messages;
		this.formats = formats;
		this.names = names;
		this.sounds = sounds;
		this.limits = limits;
		this.main = main;
		this.logger = logger;
	}

	/** Open orders the player may have, {@link OrderLimits#UNLIMITED} for no limit. */
	public int limit(Player player) {
		return limits.limit(player, settings.get().maxActive);
	}

	public long cost(Player buyer, Draft draft) {
		return Money.add(Money.multiply(draft.price(), draft.amount()), fee(buyer, draft));
	}

	public long fee(Player buyer, Draft draft) {
		if (buyer.hasPermission(Permissions.NO_FEE)) return 0;
		long total = Money.multiply(draft.price(), draft.amount());
		return Money.percent(total, settings.get().feePercent, fractional(draft.currency()));
	}

	public long payout(Player seller, Order order, int count) {
		long gross = Money.multiply(order.priceCents(), count);
		double tax = seller.hasPermission(Permissions.NO_TAX) ? 0 : settings.get().taxPercent;
		return Money.withoutPercent(gross, tax, fractional(order.currency()));
	}

	public long refund(Order order, int count) {
		return Money.multiply(order.priceCents(), count);
	}

	private boolean fractional(String currencyId) {
		Currency currency = currencies.byId(currencyId);
		return currency == null || currency.fractional();
	}

	public Problem check(Player player, Draft draft) {
		Settings settings = this.settings.get();
		if (!player.hasPermission(Permissions.CREATE)) return new Problem("error.no-permission");
		Currency currency = currencies.byId(draft.currency());
		if (currency == null || !currencies.allowed(player, currency)) return new Problem("create.no-currency");
		if (settings.isBlocked(draft.item().getType())) return new Problem("create.blocked");
		if (draft.amount() < 1 || draft.amount() > settings.maxAmount) {
			return new Problem("create.bad-amount", Arg.of("max", settings.maxAmount));
		}
		if (!draft.hasPrice()) return new Problem("create.no-price");
		Settings.CurrencySettings limits = settings.currency(currency.id());
		if (draft.price() < limits.minPrice()) {
			return new Problem("create.price-low", formats.money(player, "price", currency.id(), limits.minPrice()));
		}
		if (limits.maxPrice() > 0 && draft.price() > limits.maxPrice()) {
			return new Problem("create.price-high", formats.money(player, "price", currency.id(), limits.maxPrice()));
		}
		long cost = cost(player, draft);
		if (currency.balance(player) < cost) {
			return new Problem("create.no-money", formats.money(player, "cost", currency.id(), cost));
		}
		return null;
	}

	public void create(Player player, Draft draft, Consumer<Boolean> done) {
		Problem problem = check(player, draft);
		if (problem != null) {
			fail(player, problem.key(), problem.args());
			done.accept(false);
			return;
		}
		long now = System.currentTimeMillis();
		whenDone(exclusive(player, false, () -> storage.countOpen(player.getUniqueId(), now)
				.thenComposeAsync(open -> create(player, draft, open, now), main)), done);
	}

	private CompletableFuture<Boolean> create(Player player, Draft draft, int open, long now) {
		if (!player.isOnline()) return completedFuture(false);
		int limit = limit(player);
		if (open >= limit) {
			fail(player, "create.limit", Arg.of("limit", limit));
			return completedFuture(false);
		}
		// Checked again: the balance could change while the database was busy
		Problem problem = check(player, draft);
		if (problem != null) {
			fail(player, problem.key(), problem.args());
			return completedFuture(false);
		}
		ItemStack item = draft.item();
		if (!new OrderCreateEvent(player, item, draft.amount(), draft.price(), draft.currency()).callEvent()) {
			return completedFuture(false);
		}
		Currency currency = currencies.byId(draft.currency());
		long cost = cost(player, draft);
		// Built before the money is taken, so nothing can throw between the withdrawal and the insert
		NewOrder order = new NewOrder(player.getUniqueId(), player.getName(), item, names.searchText(item),
				draft.amount(), draft.price(), currency.id(), now, now + TimeUnit.DAYS.toMillis(draft.days()));
		if (!withdraw(currency, player, cost)) {
			fail(player, "create.no-money", formats.money(player, "cost", currency.id(), cost));
			return completedFuture(false);
		}
		return storage.create(order).handleAsync((id, error) -> {
			if (error != null) {
				if (!deposit(currency, player, cost)) {
					logger.severe("Could not refund " + Money.plain(cost) + " " + currency.id() + " to " + player.getName()
							+ " after a failed order");
				}
				if (player.isOnline()) fail(player, "error.database");
				return false;
			}
			messages.send(player, "create.done", Formats.item(item), formats.amount(player, "amount", draft.amount(), item),
					formats.money(player, "cost", currency.id(), cost), Arg.of("id", id));
			sounds.play(player, "success");
			return true;
		}, main);
	}

	public void deliver(Player seller, Order order, int requested, Consumer<Integer> done) {
		long now = System.currentTimeMillis();
		// Checked here too: shift-click in the list and the order dialog reach this without the menu checks
		if (!seller.hasPermission(Permissions.DELIVER)) {
			fail(seller, "error.no-permission");
			done.accept(0);
			return;
		}
		if (order.owner().equals(seller.getUniqueId())) {
			fail(seller, "deliver.own");
			done.accept(0);
			return;
		}
		Currency currency = currencies.byId(order.currency());
		if (!order.isOpen(now) || currency == null) {
			fail(seller, "deliver.closed");
			done.accept(0);
			return;
		}
		ItemStack template = order.item();
		int count = Math.min(Math.min(requested, order.remaining()), Items.count(seller, template));
		if (count <= 0) {
			fail(seller, "deliver.nothing", Formats.item(template));
			done.accept(0);
			return;
		}
		whenDone(exclusive(seller, 0, () -> {
			if (!new OrderDeliverEvent(seller, order, count).callEvent()) return completedFuture(0);
			List<ItemStack> taken = Items.take(seller, template, count);
			return storage.deliver(order.id(), count, now)
					.handleAsync((delivery, error) -> delivered(seller, order, currency, taken, delivery, error), main)
					.thenCompose(Function.identity());
		}), done);
	}

	private CompletableFuture<Integer> delivered(Player seller, Order order, Currency currency, List<ItemStack> taken,
			Delivery delivery, Throwable error) {
		if (error != null) {
			giveBack(seller, order, taken);
			fail(seller, "error.database");
			return completedFuture(0);
		}
		int accepted = delivery.accepted();
		Items.Split split = Items.split(taken, accepted);
		giveBack(seller, order, split.rest());
		if (accepted == 0) {
			fail(seller, "deliver.closed");
			return completedFuture(0);
		}
		long pay = payout(seller, order, accepted);
		if (!deposit(currency, seller, pay)) return revertDelivery(seller, order, split.first()).thenApply(reverted -> 0);
		// Until the next autosave the saved inventory would still hold the delivered items,
		// and a crash now would leave them both there and in the order
		if (seller.isOnline()) seller.saveData();
		messages.send(seller, "deliver.done", Formats.item(order.item()), Arg.of("count", accepted),
				formats.money(seller, "money", order.currency(), pay), Arg.of("player", order.ownerName()));
		sounds.play(seller, "sell");
		notifyOwner(order, seller, accepted, delivery.filled());
		return completedFuture(accepted);
	}

	private CompletableFuture<Void> revertDelivery(Player seller, Order order, List<ItemStack> items) {
		int count = Items.total(items);
		return storage.revertDelivery(order.id(), count).handleAsync((reverted, error) -> {
			if (error == null && reverted) {
				giveBack(seller, order, items);
				fail(seller, "deliver.payment-failed");
				return null;
			}
			logger.severe(seller.getName() + " was not paid for " + count + " items delivered to order #" + order.id()
					+ " and the owner has already collected them. Pay " + Money.plain(payout(seller, order, count)) + " "
					+ order.currency() + " manually");
			fail(seller, "deliver.payment-lost");
			return null;
		}, main);
	}

	private void giveBack(Player seller, Order order, List<ItemStack> items) {
		if (items.isEmpty()) return;
		if (seller.isOnline()) {
			Items.give(seller, items);
			return;
		}
		// The seller left while the database was answering. Losing items is worse than a rare log line
		logger.warning(seller.getName() + " left during a delivery to order #" + order.id()
				+ ", dropping " + Items.total(items) + " items at the last location");
		for (ItemStack item : items) seller.getWorld().dropItem(seller.getLocation(), item);
	}

	private void notifyOwner(Order order, Player seller, int count, boolean filled) {
		if (!settings.get().notifyDeliveries) return;
		Player owner = Bukkit.getPlayer(order.owner());
		if (owner == null) return;
		messages.send(owner, filled ? "notify.filled" : "notify.delivered", Formats.item(order.item()),
				Arg.of("count", count), Arg.of("player", seller.getName()), Arg.of("id", order.id()));
		sounds.play(owner, "notify");
	}

	public void claim(Player owner, Order order, Runnable done) {
		whenDone(claim(owner, order), ignored -> done.run());
	}

	private CompletableFuture<Void> claim(Player owner, Order order) {
		return exclusive(owner, null, () -> claimOne(owner, order).thenAcceptAsync(total -> report(owner, total), main));
	}

	public void claimAll(Player owner, Runnable done) {
		long now = System.currentTimeMillis();
		whenDone(exclusive(owner, null, () -> storage.ordersOf(owner.getUniqueId()).thenComposeAsync(orders -> {
			List<Order> ready = orders.stream().filter(order -> claimable(order, now)).toList();
			if (ready.isEmpty()) {
				fail(owner, "claim.nothing");
				return completedFuture(null);
			}
			return claimEach(owner, ready.iterator(), new ClaimTotal()).thenAcceptAsync(total -> report(owner, total), main);
		}, main)), ignored -> done.run());
	}

	private CompletableFuture<ClaimTotal> claimEach(Player owner, Iterator<Order> orders, ClaimTotal total) {
		if (!orders.hasNext() || !owner.isOnline()) return completedFuture(total);
		return claimOne(owner, orders.next()).handleAsync((result, error) -> {
			if (error == null) {
				total.add(result);
			} else {
				logUnexpected(error);
				total.failed = true;
			}
			return claimEach(owner, orders, total);
		}, main).thenCompose(Function.identity());
	}

	public static boolean claimable(Order order, long now) {
		if (order.uncollected() > 0) return true;
		return !order.isOpen(now) && !order.refunded() && order.remaining() > 0;
	}

	private CompletableFuture<ClaimTotal> claimOne(Player owner, Order order) {
		int space = Items.space(owner, order.item());
		return storage.claim(order.id(), owner.getUniqueId(), space, System.currentTimeMillis()).thenComposeAsync(claim -> {
			ClaimTotal total = new ClaimTotal();
			if (claim == null) return completedFuture(total);
			if (claim.isEmpty()) {
				total.full = claim.order().uncollected() > 0;
			} else if (owner.isOnline()) {
				handOut(owner, claim, total);
			} else {
				storage.unclaim(order.id(), claim.items(), claim.refundItems() > 0);
				return completedFuture(total);
			}
			// Completes only after the delete, otherwise a list read right after the claim can still show the order
			return storage.settle(order.id()).handle((deleted, error) -> total);
		}, main);
	}

	private void handOut(Player owner, Claim claim, ClaimTotal total) {
		Order order = claim.order();
		if (claim.items() > 0) {
			Items.give(owner, Items.stacks(order.item(), claim.items()));
			owner.saveData();
			total.items += claim.items();
		}
		total.full |= claim.items() < order.uncollected();
		if (claim.refundItems() > 0) {
			Currency currency = currencies.byId(order.currency());
			long refund = refund(order, claim.refundItems());
			if (currency != null && deposit(currency, owner, refund)) {
				total.refunds.merge(order.currency(), refund, Long::sum);
			} else {
				storage.unclaim(order.id(), 0, true);
				total.failed = true;
			}
		}
	}

	private void report(Player owner, ClaimTotal total) {
		if (total.items > 0) messages.send(owner, "claim.items", Arg.of("count", total.items));
		total.refunds.forEach((currency, amount) ->
				messages.send(owner, "claim.refund", formats.money(owner, "money", currency, amount)));
		if (total.full) messages.send(owner, "claim.full");
		if (total.failed) messages.send(owner, "claim.failed");
		if (total.items > 0 || !total.refunds.isEmpty()) sounds.play(owner, "claim");
		else if (!total.full && !total.failed) fail(owner, "claim.nothing");
	}

	public void cancel(CommandSender by, Order order, Runnable done) {
		if (by instanceof Player player && !Permissions.canCancel(player, order)) {
			fail(by, "error.no-permission");
			done.run();
			return;
		}
		if (!new OrderCancelEvent(by, order).callEvent()) {
			done.run();
			return;
		}
		CompletableFuture<Void> cancelled = storage.cancel(order.id(), System.currentTimeMillis()).thenComposeAsync(before -> {
			if (before.isEmpty()) {
				fail(by, "cancel.closed");
				return completedFuture(null);
			}
			Order closed = before.get();
			messages.send(by, "cancel.done", Formats.item(closed.item()), Arg.of("id", closed.id()));
			Player owner = Bukkit.getPlayer(closed.owner());
			if (owner == null) return completedFuture(null);
			if (owner != by) messages.send(owner, "cancel.by-staff", Formats.item(closed.item()), Arg.of("id", closed.id()));
			return claim(owner, closed);
		}, main).exceptionallyAsync(error -> {
			reportError(by, error);
			return null;
		}, main);
		whenDone(cancelled, ignored -> done.run());
	}

	/**
	 * Runs the operation under the player's lock. The result arrives on the main thread with the lock
	 * already released; an exception anywhere inside is logged and replaced by {@code fallback}.
	 */
	private <T> CompletableFuture<T> exclusive(Player player, T fallback, Supplier<CompletableFuture<T>> operation) {
		CompletableFuture<T> running = locks.tryRun(player.getUniqueId(), operation);
		if (running == null) return completedFuture(fallback);
		return running.handleAsync((result, error) -> {
			if (error == null) return result;
			reportError(player, error);
			return fallback;
		}, main);
	}

	// Callbacks reopen menus, which makes no sense while the plugin is shutting down
	private <T> void whenDone(CompletableFuture<T> operation, Consumer<T> done) {
		operation.thenAcceptAsync(result -> {
			if (!main.stopping()) done.accept(result);
		}, main).exceptionally(error -> {
			logger.log(Level.SEVERE, "Could not update the menu after an order operation", error);
			return null;
		});
	}

	private void reportError(CommandSender to, Throwable error) {
		boolean database = isDatabaseError(error);
		if (!database) logUnexpected(error);
		if (to instanceof Player player && !player.isOnline()) return;
		fail(to, database ? "error.database" : "error.unexpected");
	}

	// The storage logs its own failures
	private void logUnexpected(Throwable error) {
		if (!isDatabaseError(error)) logger.log(Level.SEVERE, "Order operation failed", unwrap(error));
	}

	private static boolean isDatabaseError(Throwable error) {
		Throwable cause = unwrap(error);
		return cause instanceof SQLException || cause instanceof RejectedExecutionException;
	}

	private static Throwable unwrap(Throwable error) {
		return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
	}

	// Economy plugins are someone else's code. An exception there must not skip the refund around these calls
	private boolean withdraw(Currency currency, Player player, long amount) {
		try {
			return currency.withdraw(player, amount);
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE, "Could not take " + Money.plain(amount) + " " + currency.id() + " from " + player.getName(), e);
			return false;
		}
	}

	private boolean deposit(Currency currency, Player player, long amount) {
		try {
			return currency.deposit(player, amount);
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE, "Could not give " + Money.plain(amount) + " " + currency.id() + " to " + player.getName(), e);
			return false;
		}
	}

	private void fail(CommandSender to, String key, Arg... args) {
		messages.send(to, key, args);
		if (to instanceof Player player) sounds.play(player, "error");
	}

	public boolean isBusy(Player player) {
		return locks.isHeld(player.getUniqueId());
	}

	private static final class ClaimTotal {
		int items;
		boolean full;
		boolean failed;
		final Map<String, Long> refunds = new LinkedHashMap<>();

		void add(ClaimTotal other) {
			items += other.items;
			full |= other.full;
			failed |= other.failed;
			other.refunds.forEach((currency, amount) -> refunds.merge(currency, amount, Long::sum));
		}
	}
}
