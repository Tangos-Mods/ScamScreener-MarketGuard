# MarketGuard 1.5.0-beta.3

**This is a beta release.** 
> Please report HUD, API, or auction-protection issues with the relevant Minecraft version and log output.

## Added

- Minion HUD forecasts based on observed production and current Bazaar prices.
- Price-volatility and liquidity warnings in the Auction Price HUD.
- A Trade Guard that compares the item value on both sides and pauses warnings when prices are incomplete or unreliable.
- Known profile-value, skill, finance, and Museum context in the Player HUD.

## Fixed

- HUD widgets no longer waste their first line on template names such as `Compact`.
- The Player HUD encounter count now uses the same clear format in-game and in the HUD editor.

## Changed

- Auction guidance and protection now use a quality-rated reference from Lowest BIN plus 7-day and 30-day market prices.
