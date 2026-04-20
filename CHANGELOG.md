## 1.2.1

- Reworked the BIN purchase flow so `Buy Item Right Now` detection no longer depends on a fragile screen-title match and now uses the configured auction slot/button data consistently.
- Overbidding now runs on the same BIN buy-click path as the blacklist warning, so both checks trigger together during the normal purchase flow.
- Overbidding no longer blocks the purchase when the auction item or SkyBlock item id could not be read; it now only sends the existing chat error in those failure cases.
- fixed Music Discs got not recognized by the Guard due to awkward Item IDs
