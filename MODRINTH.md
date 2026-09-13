# MarketGuard 1.5.0

First stable release of the 1.5.0 line. Everything below is new since 1.4.0.

## New: movable HUDs

Press `F8` to place them; each HUD can be limited to specific screens in `MarketGuard Settings > HUD`. Settings and HUD editors are available in English and German.

- **Auction Price HUD** (BIN view): the market price and one verdict that follows your protection thresholds — `Fair price`, `Good deal: 25% below market`, `12% above market`, or `25% above market - overpriced`. When the market price is uncertain, the line is marked `(few recent sales)` and above/below verdicts are softened to `Roughly ...`. Price-trend and resale warnings only appear when they matter. A coin-difference row is available in the HUD editor, hidden by default.
- **Player HUD** (`/mg playerhud <name> [profileId]`, `/mg playerhud clear`): the `trade` preset shows the name, how often you have met the player in lobbies, and whether they are on your ScamScreener blacklist; `compact` adds `Bank + purse: X` and `Est. net worth: ~X` when known; `profile`/`all` add skills, priced gear, Museum rows, a `Data warnings` row and a `Data status` row (`Updated HH:mm` / `Data may be outdated`). Missing values are hidden unless `Show unavailable Player HUD rows` is on. Presets via `/mg playerhud preset trade|compact|profile|all`, named layouts with load, edit, save/update, delete, share and clipboard import.
- **Trade Guard HUD**: compares the item value on both sides of a trade and pauses its warning when prices are incomplete or unreliable.
- **Minion Profit HUD**: `Held coins: X` and `Storage sells for: X`; **Forge Profit HUD**: `Forge items sell for: X`. A trailing `+` on either total means some stacks have no Bazaar price; a separate line counts them.
- **Profit Tracker HUD** (`/mg profit` or the `Profit Tracker Display` keybind, unbound by default): Bazaar, Auction House and Minion profit plus personal and co-op bank interest and Allowance income, with a confirmed reset action.
- Per-HUD editors with drag-and-drop row ordering, a settings screen in Mod Menu, `/mg hudlayout save|load <name>` for HUD positions, and `/mg numberformat` (`Use short coin values`) to switch between `1,000,000` and `1M`.

## Added

- An `Update notifications` setting under `General` that controls the update message shown when joining a server.
- Other mods can reuse MarketGuard's market and player data instead of sending their own requests.

## Changed

- Auction verdicts and the overbidding block compare against a blend of Lowest BIN and the 7-day and 30-day average prices instead of Lowest BIN alone.

## Fixed

- Level-100 pets and Common/Uncommon pets are compared against the correct reference price; a `[Lvl 100]` pet no longer triggers a false overbidding block.
- The Profit Tracker no longer multiplies Bazaar instant buy/sell totals by the stack size, counts partially claimed buy orders correctly, and forgets cancelled unfilled orders.
- A crash while saving no longer resets your all-time Profit Tracker totals.
- The Trade Guard no longer shows blacklist notices about unrelated auction sellers.
- The Player HUD says `Data may be outdated` and `Finance data unavailable` instead of claiming finance-based values it does not have, and no longer fails to load for some players.
- Update notifications only appear when the listed release is newer than the installed version.
- HUDs stay visible on Turkish/Azeri system locales, reordering Player HUD rows for one preset no longer drops the rows of the other presets, and a HUD disabled on every screen stays disabled after a restart.
- `/mg reload` applies a changed Player HUD preset immediately, and command feedback names the real config file (`config/scamscreener_marketguard/config.json`).

## Performance

- The Trade Guard, Minion, Forge and Auction Price HUDs cost far less frame rate while their menus are open.
- Lobby encounters are recorded in the background instead of stalling the game every 10 seconds.
- The Profit Tracker does no work while its HUD is disabled and forgets auction positions and listings older than 14 days.
