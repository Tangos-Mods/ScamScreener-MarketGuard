# ScamScreener MarketGuard

`ScamScreener MarketGuard` is a client-side Fabric mod that protects you from expensive misclicks in the SkyBlock Auction House. It compares prices in relevant BIN auction screens against the current `Lowest BIN` and blocks risky clicks before you lose coins or accidentally list an item far too cheaply.

## What The Mod Does

MarketGuard currently steps in during two common risk situations:

- When creating a `Create BIN Auction`, the mod checks whether your listed price is significantly below the `Lowest BIN`.
- When opening a `Bin Auction View`, the mod checks whether the purchase price is significantly above the `Lowest BIN`.

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
- `/marketguard underbidding <0-100>`
- `/marketguard overbidding <value 100 or higher>`

Examples:

- `/marketguard underbidding 85` allows at most `15%` below `Lowest BIN`.
- `/marketguard overbidding 130` allows at most `30%` above `Lowest BIN`.

Disabling protection:

- `underbidding 0` or `underbidding 100` disables underbidding protection.
- `overbidding 100` disables overbidding protection.

The values are stored in `config/scamscreener_marketguard/config.json`.

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
