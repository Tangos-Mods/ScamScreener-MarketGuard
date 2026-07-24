# MarketGuard 1.5.0-beta.1

**This is a beta release.** 
> Please report HUD, API, or auction-protection issues with the relevant Minecraft version and log output.

## Added

- MidnightLib settings with separate editors for Auction Price, Player, Minion Profit, Forge Profit, and Profit Tracker HUDs.
- `F8` as the default key to move and arrange HUD elements.
- Player HUD presets: Trade, Compact, Profile, and All.
- Player HUD layout management with load, edit, save/update, delete, share, and clipboard import.
- Player/profile data loading with partial-result handling, stale-cache indicators, and unavailable-field display.
- ScamScreener Fabric API integration for local blacklist status, including a selectable and localized HUD row.
- Local UUID and encounter fallbacks when the remote Player API is unavailable.
- Confirmed reset action for all persisted Profit Tracker data.
- Co-op bank interest and Allowance income in the Profit Tracker.

## Fixed

- Improved Lowest BIN handling, auctioneer blacklist checks, config migration, and HUD editor stability.

## Changed

- Auction Price HUD differences now use the API's seven-day average.
- Player HUD row lists now follow the selected preset while preserving saved visibility choices.
- Added localized English and German HUD labels and clearer active/inactive screen indicators.

Known beta limitation: extended player fields such as wealth, equipment, pets, and skills depend on the remote MarketGuard Player API. Local name, UUID, encounter, and ScamScreener data remain available when possible.
