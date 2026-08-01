# ScamScreener MarketGuard

> **Beta release:** Version 1.5.0-beta.2 is currently published as a beta release.

Beta 2 fixes Player HUD requests through HTTP/1.1 proxies by decoding chunked Player API responses correctly. It also caches failed ScamScreener blacklist lookups to avoid repeated console output.

`ScamScreener MarketGuard` is a client-side Fabric mod that protects you from expensive misclicks in the SkyBlock Auction House. It compares prices in relevant BIN auction screens against the current `Lowest BIN` and blocks risky clicks before you lose coins or accidentally list an item far too cheaply.

## What The Mod Does

MarketGuard currently steps in during two common risk situations:

- When creating a `Create BIN Auction`, the mod checks whether your listed price is significantly below the `Lowest BIN`.
- When opening a `Bin Auction View`, the mod checks whether the purchase price is significantly above the `Lowest BIN`.
- In a `Bin Auction View`, the movable `Auction Price` HUD card shows the offered price, `Lowest BIN`, and their difference. It highlights offers at least 5% above `Lowest BIN` and especially cheap offers at least 20% below it.

If a price falls outside your configured tolerance, the click is blocked and you receive a clear chat warning with the item name and percentage difference. That gives you an extra safety stop before an expensive mistake goes through.

## How It Helps You As A Player

- Protects you from typo prices, missing zeroes, and careless input when listing a BIN auction.
- Prevents impulsive misbuys when a BIN offer is far above the usual market price.
- Saves coins by stepping in at the exact moment a misclick would become expensive.
- Stays client-side: no server installation, no interaction with other players, no unnecessary overhead.

The mod is intentionally not a general-purpose scam scanner for everything. It is a direct price safety layer for the most critical BIN clicks in the Auction House.

## How The Protection Works

1. You open a relevant auction screen.
2. MarketGuard preloads `Lowest BIN` data in the background.
3. On a risky click, the mod compares the shown price against the market value.
4. If the difference is too large, the click is stopped and a warning is shown.
5. If you still want to continue on purpose, the block can be bypassed after several additional clicks.

This means accidental mistakes get caught, but intentional decisions are still possible.

## Default Protection Values

By default, MarketGuard uses these thresholds:

- `Underbidding: 80%`
  This blocks auctions listed more than `20%` below `Lowest BIN`.
- `Overbidding: 120%`
  This blocks purchases that are more than `20%` above `Lowest BIN`.

## Commands

You can adjust the protection thresholds directly in-game:

- `/marketguard`
- `/mg`
- `/marketguard reset`
- `/marketguard reload`
- `/marketguard debug`
- `/marketguard threshold <number>`
- `/marketguard underbidding <0-100>`
- `/marketguard overbidding <value 100 or higher>`
- `/marketguard playerhud <Spielername-oder-UUID> [Profil-UUID]`
- `/marketguard playerhud clear`
- `/mg profit` (alias `/mg profittracker`)
- `/mg numberformat` toggles financial HUD values between `1,000` and `1k` notation

Examples:

- `/marketguard underbidding 85` allows at most `15%` below `Lowest BIN`.
- `/marketguard overbidding 130` allows at most `30%` above `Lowest BIN`.
- `/marketguard reset` restores the default thresholds `80/120`.
- `/marketguard reload` reloads the values from `config/scamscreener_marketguard/config.json`.
- `/marketguard debug` toggles debug logging in the config file.
- `/marketguard threshold 10000` requires at least `10,000` coins difference to block.
- `/marketguard playerhud Pankraz01` shows the selected SkyBlock profile in the movable `Trade Check` HUD card. Press `F8` to place visible MarketGuard HUDs, including while a container screen is open.
- In `MarketGuard Settings > HUD > Player HUD > Edit > Layouts`, named layouts can be selected, edited, updated, deleted, copied as a share code, or imported from the clipboard.
- The Player HUD includes an `All` preset and can optionally show unavailable rows as `n/a`.
- `/mg profit` toggles the movable, persistent `Profit Tracker` HUD. Its `Profit Tracker Display` keybind is `NONE` by default and can be assigned in Minecraft's Controls menu.

### Mod integration API

Other client-side mods can reuse MarketGuard's already loaded market data without creating another request:

```java
import eu.tango.scamscreener.marketguard.api.MarketGuardApi;

var lowestBin = MarketGuardApi.lookupCachedLowestBin("HYPERION");
if (lowestBin.hasValue()) {
    double price = lowestBin.value();
}

MarketGuardApi.requestLowestBin("HYPERION").thenAccept(latest -> {
    if (latest.hasValue()) {
        double price = latest.value();
    }
});
```

`lookupCachedLowestBin`, `lookupCachedBazaarProduct`, and `lookupCachedPlayer` are cache-only: they neither request data nor start a refresh. `requestLowestBin`, `requestBazaarProduct`, and `requestPlayer` use MarketGuard's shared request/cache path, so concurrent mods do not create duplicate API requests. `stale`, `loading`, and `refreshFailed` describe the cache state.

### Local API development

Build a development JAR that uses the local MarketGuard API instead of `scamscreener.creepans.net`:

```bash
GRADLE_USER_HOME="$PWD/.gradle-user-home" ./gradlew buildDevJars
```

The generated `-dev.jar` files use `http://localhost:8081` for Lowest BIN, Bazaar and player HUD requests. Start the API with `uvicorn app.marketguard_api.main:create_marketguard_app --factory --host 0.0.0.0 --port 8081`. A different base URL can be supplied through `-PdevApiBaseUrl=http://localhost:9090`.

For an IntelliJ development client, use `Minecraft Client Dev (:26.1.2)` or `Minecraft Client Dev (:26.2)`. These configurations launch the project classes with `-Dmarketguard.apiBaseUrl=http://localhost:8081`, so they use the same local API without installing a JAR first.

Disabling protection:

- `underbidding 0` or `underbidding 100` disables underbidding protection.
- `overbidding 100` disables overbidding protection.

The values are stored in `config/scamscreener_marketguard/config.json`. MidnightLib exposes the complete client configuration in Mod Menu when Mod Menu is installed.

MarketGuard only blocks when both conditions are met:

- the configured percentage threshold is exceeded
- the absolute coin difference to `Lowest BIN` is at least the configured threshold

## Important Notes

- The mod uses `Lowest BIN` data from the external ScamScreener API.
- MarketGuard refreshes `Lowest BIN` data when you open an Auction House screen and uses the cached snapshot for price checks.
- If the API is slow or temporarily unavailable, MarketGuard does not block AH actions just because price data is missing.
- If a refresh fails, the mod shows one warning and keeps using cached data when available.
- On join, the mod can also notify you when a new version is available.

## Build

1. Run `./gradlew build` (Linux/macOS) or `gradlew.bat build` (Windows).
2. Use `buildAndCollect` to collect remapped artifacts under `build/libs/<mod.version>/`.

## Project Notes

- Mod id: `marketguard`
- Loader: Fabric
- Environment: Client only
