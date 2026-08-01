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
