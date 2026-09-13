# MarketGuard 1.5.0

First stable release of the 1.5.0 line. Everything below is new since 1.4.0.

## Added

- Price-volatility and liquidity warnings in the Auction Price HUD.
- A Trade Guard that compares the item value on both sides and pauses warnings when prices are incomplete or unreliable.
- Known profile-value, skill, finance, and Museum context in the Player HUD.
- A Player HUD layout picker with load, edit, save/update, delete, share, and clipboard import actions.
- The `All` Player HUD preset and an option to render unavailable values as `n/a`.
- Personal and co-op bank interest plus Allowance income in the Profit Tracker HUD, and a confirmed reset action for its data.
- Per-HUD editors with drag-and-drop row ordering, plus a MidnightLib settings screen in Mod Menu.
- An `Update notifications` setting under `General` that controls the Modrinth update message shown when joining a server.

## Fixed

- Level-100 pets and Common/Uncommon pets are now compared against the correct reference price; a `[Lvl 100]` pet no longer triggers a false overbidding block.
- The Profit Tracker no longer multiplies Bazaar instant buy/sell totals by the stack size, counts partially claimed buy orders correctly, and forgets cancelled unfilled orders.
- The Trade Guard no longer shows blacklist notices about unrelated auction sellers.
- The Player HUD warns when its data may be outdated and says so when finance data is unavailable instead of claiming finance-based values.
- Update notifications only appear when the listed release is newer than the installed version.
- HUDs stay visible on Turkish/Azeri system locales, editor row order is kept per preset, and a HUD disabled on every screen stays disabled after a restart.
- The Profit Tracker file is saved atomically, so a crash while saving no longer resets your all-time profits.
- HUD widgets no longer waste their first line on template names such as `Compact`.
- The Player HUD encounter count uses the same clear format in-game and in the HUD editor.
- Player HUD requests through HTTP/1.1 proxies decode chunked responses correctly.
- The UUID row stays visible in the Player HUD's `profile` preset.

## Performance

- Trade Guard, Minion, Forge, and Auction Price HUDs read the container once per tick instead of every frame, and item-name lookups no longer scan the whole Bazaar list.
- Lobby encounters are recorded in the background instead of stalling the game every 10 seconds.
- The Profit Tracker forgets auction positions and listings older than 14 days, so its file stays small.

## Changed

- HUDs speak plain language now. The Auction Price HUD shows the market price and one verdict (`Fair price`, `Good deal: 25% below market`, `18% above market - overpriced`) that follows your protection thresholds; the coin difference row is still available in the HUD editor but hidden by default. Price-trend and resale warnings only appear when they matter.
- The Player HUD `trade` preset shows only the name, how often you have seen the player, and the blacklist status; `compact` adds bank + purse and an estimated net worth when known. The data-source line, finance-status rows and partial-data notes are gone; `n/a` rows only appear with the `Show unavailable Player HUD rows` option.
- The Minion and Forge HUDs show the held coins and what the items sell for right now; the Minion production forecast that required waiting in the minion menu was removed.
- Auction guidance and protection use a quality-rated reference from Lowest BIN plus 7-day and 30-day market prices.
- Other mods can share MarketGuard's market and player data through the new `marketguard-api` entrypoint instead of sending their own requests.
