## 1.1.0

- Added `/marketguard` and `/mg` client commands to inspect and change underbidding and overbidding thresholds.
- Persisted MarketGuard settings in `config/scamscreener_marketguard/config.json`.
- Added optional debug logging for auction clicks, bypass handling, price parsing, and Lowest BIN lookups.
- Moved Lowest BIN refreshes out of the click path to avoid client stalls and prefetched prices when auction screens open.
- Guard now blocks clicks while Lowest BIN data is loading and falls back to stale in-memory prices with a warning if refreshes fail.
