## 1.5.0-beta.3

## Added

- Add observed Minion production forecasts using current Bazaar instant-sell prices.
- Add auction price volatility and Bazaar liquidity warnings.
- Add an item-side Trade Guard for the Hypixel trade screen with conservative warning suppression when prices are missing, stale, or low confidence.
- Add known profile-value, skill, finance, and Museum context through the normalized ScamScreener API finance contract.

## Fixed

- Keep the Player HUD encounter count consistent between the live widget and editor preview.

## Changed

- Use a quality-rated reference assembled from Lowest BIN, 7-day, and 30-day auction prices for HUD guidance and auction protection.
- Remove the template title row from every HUD widget and from the HUD row editor.
- Remove obsolete `title` rows from existing HUD configurations during normalization.

## 1.5.0-beta.2

## Fixed

- Cache the Player HUD's ScamScreener blacklist lookup to avoid repeated render-time lookups and debug-log spam.
- Parse chunked HTTP responses from the Player API correctly.
- Keep the UUID row visible in the Player HUD's `profile` preset.

## Changed

- Player HUD requests without an explicit `profileId` now work with the API's selected-profile resolution.

## 1.5.0-beta.1

- Calculate the Auction Price HUD difference against the API's 7-day average instead of Lowest BIN.
- Keep rendering available player fields when the Player API reports partial or unavailable data.
- Add the `All` Player HUD preset and an option to render unavailable values as `n/a`.
- Move the Player HUD profile selector into the dedicated Player HUD editor.
- Show configured `n/a` rows even when the Player API request itself is unavailable.
- Add a Player HUD layout picker with load, edit, save/update, delete, share, and clipboard import actions.
- Expose a typed `PlayerApiUnavailableException` when the reachable Player API endpoint returns no usable result.
- Show real HUD examples in the row editor and move rows between visible and hidden columns with drag and drop.
- Add support for Minecraft 26.2.x
- Add MidnightLib-backed client configuration for auction protection and MarketGuard HUDs.
- Track personal and co-op bank interest together, plus Allowance income, in the Profit Tracker HUD.
- Add a confirmed settings action for resetting all persisted Profit Tracker data.
- Fix the MidnightLib HUD settings crash caused by missing metadata on custom controls.
- Align the custom HUD editor with MidnightLib's layout and hide duplicate raw list fields.
- Split HUD settings into per-HUD editors with drag-and-drop row ordering.
- Open the settings screen one tick after `/mg` or `/marketguard` so chat closing cannot immediately hide it.

## 1.4.0

- Add support for Minecraft 26.2.x
