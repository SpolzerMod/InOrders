<div align="center">

# InOrders

**Buy orders for Paper: a player pays for the items up front, anyone who has them delivers and is paid on the spot.**

<a href="#english"><img src="docs/images/flag-en.svg" width="22" alt="EN"> English</a> · <a href="#russian"><img src="docs/images/flag-ru.svg" width="22" alt="RU"> Русский</a>

</div>

---

<a id="english"></a>

## Overview

InOrders is the reverse of an auction house. Instead of listing goods, a player posts what they want to buy, for example "640 oak logs at 2 coins each". The full amount is withdrawn when the order is created and held by the plugin, so a seller never has to trust the buyer. Anyone with matching items hands them in and is paid immediately; the buyer collects the goods later.

The forms are built on the dialog screens added in Minecraft 1.21.6. Bedrock players and older clients get the same forms as chest menus. Nothing has to be installed on the client.

- Item catalog grouped like the creative inventory, with search in English and Russian
- Orders by example: the exact item from the player's hand, enchantments and potion effects included
- Money through Vault or PlayerPoints, optional creation fee and delivery tax
- Order slots per rank through permissions or LuckPerms meta
- SQLite out of the box, MySQL/MariaDB for single servers and networks
- Messages per client language (English and Russian included, MiniMessage format)
- A small API module with order data and cancellable events

## Requirements

| | |
| --- | --- |
| Server | Paper 1.21.7 - 26.3, one jar for every version |
| Java | Whatever your Paper version needs, 21 or newer |
| Currency | Vault with an economy plugin, or PlayerPoints |
| First start | Internet access, Paper downloads HikariCP and the MySQL driver |

Optional integrations:

| Plugin | Used for |
| --- | --- |
| Vault + economy | Orders paid with money |
| PlayerPoints | Orders paid with points |
| LuckPerms | Order slots from the `inorders-limit` meta, contexts included |
| Floodgate | Native text input forms for Bedrock players |
| ViaVersion | Detecting clients older than 1.21.6, which get chest menus |

Item icons on the catalog buttons need 1.21.11 or newer on both server and client; older versions show the item name only.

## Building

The build uses the Gradle wrapper, no local Gradle install is needed.

```sh
git clone https://github.com/SpolzerMod/InOrders.git
cd InOrders
./gradlew build
```

| Task | What it does | JDK |
| --- | --- | --- |
| `./gradlew build` | Compiles everything, runs `verifyLatestApi`, builds both jars | 21 to run Gradle, 25 installed for the check |
| `./gradlew :plugin:shadowJar` | Plugin jar only, no API check | 21 |
| `./gradlew :plugin:verifyLatestApi` | Compiles the plugin against Paper 26.3 | 25 |

Output:

- `plugin/build/libs/InOrders-1.0.0.jar` - the server plugin
- `api/build/libs/api-1.0.0.jar` - the API for other plugins

The plugin is compiled against Paper API 1.21.7 with `--release 21`, the oldest supported version. `verifyLatestApi` compiles the same sources against Paper 26.3 so that removed or changed methods show up at build time. Gradle picks up an installed JDK 25 automatically through toolchains.

Runtime libraries (HikariCP, MySQL Connector/J) are not shaded. They are listed under `libraries:` in `plugin.yml` and downloaded by Paper.

## Project layout

```
InOrders/
├── api/                      public API: InOrders, Order, OrderStatus, events
├── plugin/
│   └── src/main/
│       ├── java/me/spolzer/inorders/
│       │   ├── catalog/      creative tab catalog, item names and icons
│       │   ├── command/      /orders
│       │   ├── config/       config.yml
│       │   ├── currency/     Vault and PlayerPoints, money arithmetic
│       │   ├── dialog/       dialog forms (1.21.6+ clients)
│       │   ├── input/        client detection, Floodgate forms, chat input
│       │   ├── listener/     menu clicks, join reminders
│       │   ├── menu/         chest menus
│       │   ├── order/        order logic: create, deliver, claim, cancel
│       │   ├── permission/   permission nodes, order limits, LuckPerms meta
│       │   ├── storage/      SQLite and MySQL access
│       │   └── text/         messages, formatting, number input
│       └── resources/
│           ├── assets/       vanilla item names (en, ru) and item sprite paths
│           ├── lang/         en.yml, ru.yml
│           ├── config.yml
│           └── plugin.yml
└── docs/images/
```

## How it works

**Escrow.** Creating an order withdraws `price × amount + fee` at once. Deliveries are paid from that money minus the tax, and whatever is left when an order is cancelled or expires goes back to the buyer. The buyer takes the delivered items and the refund from *My orders*; the database only keeps counters (`delivered`, `collected`, `refunded`), the items themselves are rebuilt from the order template.

**Money.** All amounts are `long` values in hundredths of a unit, for every currency: 2.50 coins is 250, 5 points is 500. Fees and taxes are calculated with `BigDecimal` and rounded down, so rounding never creates money. Doubles only appear at the Vault boundary.

**Threads.** No database call runs on the main thread. Writes go through a single storage thread, so two deliveries to the same order never interleave on one server; with MySQL, reads use virtual threads and servers sharing the database are kept apart by `SELECT ... FOR UPDATE` inside transactions. Callbacks come back to the main thread through a queue that is also drained in `onDisable`, so a delivery the database has already saved still pays the seller during shutdown.

**Double clicks and duplication.** A player can only run one money or item operation at a time. After every delivery the seller's player file is saved immediately, so a crash cannot leave the items both in the inventory and in the order.

**Version differences.** The creative tabs are not part of the Paper API; the catalog reads them by reflection with Mojang mappings (`paperweight-mappings-namespace: mojang`) and falls back to one alphabetical tab. Client versions are read from ViaVersion when it is installed.

## Installation

1. Put `InOrders-1.0.0.jar` into `plugins/`.
2. Restart the server. `config.yml`, `lang/en.yml` and `lang/ru.yml` are created in `plugins/InOrders/`.
3. Adjust the config if needed and run `/orders reload`. Settings under `storage` need a restart.

## Commands

| Command | Description |
| --- | --- |
| `/orders` | Order list |
| `/orders my` | Your orders: progress, collecting, cancelling |
| `/orders create [hand]` | New order; `hand` uses the item in your hand |
| `/orders search <text>` | Order list filtered by item name |
| `/orders open <player> [my]` | Open the menu for another player, for NPCs and menu plugins |
| `/orders reload` | Reload the config and language files |

Aliases: `/order`, `/inorders`.

## Permissions

| Permission | Default | Access |
| --- | --- | --- |
| `inorders.player` | everyone | All player permissions below |
| `inorders.use` | everyone | Order list and *My orders* |
| `inorders.create` | everyone | Creating orders |
| `inorders.deliver` | everyone | Delivering items |
| `inorders.cancel` | everyone | Cancelling own orders |
| `inorders.currency.money` | everyone | Orders paid with Vault money |
| `inorders.currency.playerpoints` | everyone | Orders paid with points |
| `inorders.limit.<number>` | - | Number of open orders |
| `inorders.limit.unlimited` | - | No limit on open orders |
| `inorders.bypass.fee` | - | No creation fee |
| `inorders.bypass.tax` | - | No delivery tax |
| `inorders.admin` | op | All staff permissions below |
| `inorders.admin.cancel` | op | Remove other players' orders, the buyer is refunded |
| `inorders.admin.open` | op | `/orders open` |
| `inorders.admin.reload` | op | `/orders reload` |
| `inorders.admin.worlds` | op | Use orders in `disabled-worlds` |

Wildcards: `inorders.*`, `inorders.currency.*`, `inorders.bypass.*`.

To give orders to certain ranks only, set `permissions.open-to-everyone: false`, run `/orders reload` and grant `inorders.player` to the group:

```
/lp group rank3 permission set inorders.player true
```

**Order slots** are resolved in this order: `inorders.limit.unlimited`, the LuckPerms meta `inorders-limit`, the highest `inorders.limit.<number>`, then `orders.max-active`. A permission replaces the config value, so it can also lower it.

```
/lp group default permission set inorders.limit.3 true
/lp group vip meta set inorders-limit 5
/lp group admin permission set inorders.limit.unlimited true
```

<details>
<summary><b>config.yml</b></summary>

```yaml
# InOrders configuration.
# Messages and menu texts are in lang/<language>.yml.

# "auto" shows every player the messages in their game language. English and Russian are included,
# other languages can be added as new files in lang/. Any other value (for example "ru") uses one
# language for everyone.
language: auto
# Used for the console and for game languages without a file in lang/.
default-language: en

permissions:
  # true: all players can use orders by default.
  # false: players need inorders.player (or individual permissions) from a permissions plugin such as LuckPerms.
  # Staff permissions are always limited to operators by default.
  open-to-everyone: true

menus:
  # How forms are shown: creating an order, choosing an amount, entering a price or a search.
  # auto: game dialogs (Minecraft 1.21.6 and newer), chest menus for Bedrock players and older clients.
  # dialog: dialogs wherever the client supports them.
  # chest: chest menus and chat input for everyone.
  # With dialogs the item catalog is a dialog too, with item icons for clients 1.21.11 and newer.
  # Order lists are always chest menus.
  forms: auto
  # Creative tabs hidden from the item catalog. Tab ids: building_blocks, colored_blocks, natural_blocks,
  # functional_blocks, redstone_blocks, tools_and_utilities, combat, food_and_drinks, ingredients, spawn_eggs.
  hidden-tabs:
    - spawn_eggs

orders:
  # Open orders per player without a limit permission. inorders.limit.<number> or the LuckPerms meta
  # inorders-limit replace it, inorders.limit.unlimited removes the limit.
  max-active: 7
  # Largest number of items in one order. 2304 is a full inventory of 64-item stacks.
  max-amount: 2304
  # Durations in days a player can choose from.
  durations: [1, 3, 7]
  default-duration: 3
  # Paid on top of the order price when it is created. Not returned when the order is cancelled.
  fee-percent: 0
  # Taken from the money a seller receives for delivered items.
  tax-percent: 0

currencies:
  # Requires Vault and an economy plugin.
  money:
    enabled: true
    # Price limits for one item. 0 as max-price means no limit.
    min-price: 0.01
    max-price: 0
  # Requires PlayerPoints.
  playerpoints:
    enabled: true
    min-price: 1
    max-price: 0

# Items that cannot be ordered.
blocked-items: []
#  - BEDROCK
#  - BARRIER

# Worlds where the order menus cannot be opened.
disabled-worlds: []

notifications:
  # Tell online owners when items are delivered to their orders.
  deliveries: true
  # Remind players on join that they have items or refunds to collect.
  join-reminder: true

# Format: "<sound> [volume] [pitch]". Leave empty to turn a sound off.
sounds:
  click: "ui.button.click 0.4 1.2"
  success: "entity.player.levelup 0.6 1.4"
  sell: "entity.experience_orb.pickup 0.8 1.1"
  claim: "entity.item.pickup 0.8 0.9"
  notify: "block.note_block.chime 0.8 1.2"
  error: "entity.villager.no 0.6 1"

# Changes in this section require a server restart.
storage:
  # sqlite: local file plugins/InOrders/orders.db, no setup required.
  # mysql: MySQL or MariaDB server, configured below.
  type: sqlite
  # Unique name of this server. Only needed when several servers use the same MySQL database.
  server-id: main
  # true: orders from every server sharing the database are visible everywhere.
  # Only enable this when all these servers share one economy, otherwise money moves between economies.
  shared-orders: false
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    user: root
    password: ""
    # Number of connections.
    pool-size: 4
    # Additional JDBC driver properties.
    properties:
      useSSL: false
      allowPublicKeyRetrieval: true
```

</details>

## Database

SQLite is used by default (`plugins/InOrders/orders.db`, WAL mode). For MySQL or MariaDB set `storage.type: mysql` and fill in `storage.mysql`. Everything lives in one table that is created on start:

| Column | Meaning |
| --- | --- |
| `id` | Order number |
| `server` | `storage.server-id` of the server the order belongs to |
| `owner`, `owner_name` | Buyer UUID and name |
| `item` | Order template, serialized by Paper |
| `search` | Item id and names (with enchantments and potion effects) in every bundled language, lowercase |
| `amount`, `delivered`, `collected` | Ordered, handed in, taken out by the buyer |
| `price_cents`, `currency` | Price of one item in hundredths, currency id |
| `created`, `expires` | Epoch milliseconds |
| `status`, `refunded` | `ACTIVE`, `FILLED`, `CANCELLED`, `EXPIRED`; whether the unspent money was returned |

Servers sharing a database see only their own orders. `storage.shared-orders: true` shows orders from the whole network, which only makes sense with a shared economy.

## API

Build the `api` module (or take `api-1.0.0.jar` from a release), add it as `compileOnly` and put InOrders into `depend` or `softdepend`.

```kotlin
dependencies {
    compileOnly(files("libs/api-1.0.0.jar"))
}
```

```java
InOrders orders = InOrders.get();
orders.orders(player.getUniqueId()).thenAccept(list ->
    getLogger().info(player.getName() + " has " + list.size() + " orders"));
```

| Member | Notes |
| --- | --- |
| `InOrders#order(long)`, `InOrders#orders(UUID)` | Return `CompletableFuture`, completed off the main thread |
| `InOrders#openMenu(Player)` | Opens the order list, main thread only |
| `InOrders#currencies()` | Ids of the enabled currencies |
| `Order#priceCents()` / `Order#price()` | Price in hundredths / as `BigDecimal` |
| `OrderCreateEvent` | Before the payment is taken, cancellable |
| `OrderDeliverEvent` | Before the items are taken from the seller, cancellable |
| `OrderCancelEvent` | When the owner or staff cancels, cancellable |

## FAQ

**Does the client need a mod or a resource pack?** No.

**Can the dialogs be turned off?** Yes, `menus.forms: chest` uses chest menus for everyone.

**What if the buyer is offline when an order is filled?** Items and money wait in the order, and the buyer is reminded on join.

**Is it crash safe?** Player files are saved right after deliveries and saved operations are finished on shutdown. The economy plugin and the database are still separate systems, so a hard crash in the middle of an operation cannot be ruled out completely.

## License

[MIT](LICENSE). Bugs and ideas go to [issues](../../issues).

---

<a id="russian"></a>

## Описание

InOrders работает наоборот по сравнению с аукционом. Игрок не выставляет товар, а сообщает, что хочет купить, например «640 дубовых брёвен по 2 монеты». Сумма списывается сразу и хранится в заказе, поэтому продавцу не нужно доверять покупателю. Любой, у кого есть подходящие предметы, сдаёт их и сразу получает оплату, а заказчик забирает покупку позже.

Формы построены на игровых окнах Minecraft 1.21.6. Bedrock-игроки и более старые клиенты получают те же формы в виде меню-сундуков. На клиент ничего ставить не нужно.

- Каталог предметов по разделам творческого режима, поиск на русском и английском
- Заказ по образцу: точно такой же предмет, как в руке, с чарами и эффектами зелий
- Деньги Vault или поинты PlayerPoints, комиссия при создании и налог при сдаче по желанию
- Слоты заказов по рангам через права или мету LuckPerms
- SQLite без настройки, MySQL/MariaDB для одного сервера или сети
- Язык сообщений по клиенту, в комплекте русский и английский (MiniMessage)
- Модуль API с данными заказов и событиями

## Требования

- Paper 1.21.7 - 26.3, один jar на все версии.
- Java той версии, которую требует ваш Paper (минимум 21).
- Vault с плагином экономики или PlayerPoints.
- Интернет при первом запуске: Paper скачает HikariCP и драйвер MySQL.

Необязательно: LuckPerms (мета `inorders-limit`), Floodgate (формы для Bedrock), ViaVersion (меню-сундуки для клиентов младше 1.21.6). Иконки предметов в каталоге видны с 1.21.11 на сервере и клиенте.

## Сборка

```sh
git clone https://github.com/SpolzerMod/InOrders.git
cd InOrders
./gradlew build
```

- `./gradlew build` - полная сборка с проверкой `verifyLatestApi`. Gradle запускается на JDK 21, для проверки нужен установленный JDK 25.
- `./gradlew :plugin:shadowJar` - только jar плагина, хватит JDK 21.

Готовые файлы: `plugin/build/libs/InOrders-1.0.0.jar` и `api/build/libs/api-1.0.0.jar`.

Плагин компилируется против Paper API 1.21.7 с `--release 21`, а `verifyLatestApi` собирает те же исходники против Paper 26.3, чтобы удалённые методы находились ещё при сборке. HikariCP и драйвер MySQL не упаковываются в jar, их загружает Paper по списку `libraries:` в `plugin.yml`.

## Как устроено

**Эскроу.** При создании списывается `цена × количество + комиссия`. Из этой суммы платятся продавцы за вычетом налога, остаток при отмене или окончании срока возвращается заказчику. В базе хранятся только счётчики `delivered`, `collected`, `refunded`, сами предметы восстанавливаются по шаблону заказа.

**Деньги.** Все суммы - `long` в сотых для любой валюты: 2.50 монеты это 250, 5 поинтов это 500. Комиссия и налог считаются через `BigDecimal` с округлением вниз. `double` используется только при обращении к Vault.

**Потоки.** Запросы к базе не выполняются в главном потоке. Записи идут через один поток хранилища, чтение в MySQL через виртуальные потоки, серверы с общей базой разделяются блокировкой строк `SELECT ... FOR UPDATE`. Результаты возвращаются в главный поток через очередь, которую `onDisable` дорабатывает до конца, поэтому уже сохранённая сдача оплачивается и при выключении сервера.

**Дюпы и двойные клики.** У игрока одновременно выполняется только одна операция с деньгами или предметами. После каждой сдачи файл продавца сохраняется сразу.

## Установка и команды

Положите jar в `plugins/` и перезапустите сервер. Настройки лежат в `plugins/InOrders/config.yml` (полный файл с комментариями есть в английском разделе выше), изменения применяются командой `/orders reload`, кроме раздела `storage`.

| Команда | Описание |
| --- | --- |
| `/orders` | Список заказов |
| `/orders my` | Ваши заказы |
| `/orders create [hand]` | Новый заказ, с `hand` - на предмет из руки |
| `/orders search <текст>` | Поиск заказов по названию |
| `/orders open <игрок> [my]` | Открыть меню другому игроку |
| `/orders reload` | Перезагрузить настройки и переводы |

Права те же, что в таблице выше. Слоты заказов проверяются по порядку: `inorders.limit.unlimited`, мета LuckPerms `inorders-limit`, наибольшее `inorders.limit.<число>`, затем `orders.max-active`. Право заменяет значение из конфига, поэтому может его и уменьшить:

```
/lp group default permission set inorders.limit.3 true
/lp group vip meta set inorders-limit 5
```

Чтобы открыть заказы только определённым рангам, укажите `permissions.open-to-everyone: false`, выполните `/orders reload` и выдайте группе `inorders.player`.

## Лицензия

[MIT](LICENSE). Ошибки и предложения - в [issues](../../issues).
