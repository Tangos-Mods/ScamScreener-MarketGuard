## 1.2.0

- Lowest BIN lookups now use the new ScamScreener API `v2` payload with `products`, `price`, and `auctioneerUuid`.
- Added optional ScamScreener integration as a soft dependency via Modrinth Maven and Fabric `suggests`, so MarketGuard can check Lowest BIN auctioneers against your ScamScreener blacklist when the mod is installed.
- Auction screens now warn with `[MarketGuard] <player> is listed in your blacklist! Be cautious!` when the current Lowest BIN auctioneer is blacklisted, including `Confirm Purchase`, without blocking the normal Lowest BIN comparison flow.
- Added detailed debug logs for the ScamScreener blacklist check path, including lookup trigger, UUID parsing, match/no-match results, and duplicate-notice suppression. (Only when debug=true in config)
