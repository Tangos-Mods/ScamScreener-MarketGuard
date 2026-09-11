package eu.tango.scamscreener.marketguard.api;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;

import java.util.concurrent.CompletableFuture;

/**
 * Fabric entrypoint backing {@link MarketGuardApi} with MarketGuard's own caches and config.
 */
public final class MarketGuardApiEntrypoint implements MarketGuardApi {
    private static final MarketGuardSettingsApi SETTINGS = new MarketGuardSettingsApi() {
        @Override
        public boolean updateNotificationsEnabled() {
            return MarketGuardConfig.isUpdateNotificationsEnabled();
        }

        @Override
        public void setUpdateNotificationsEnabled(boolean enabled) {
            if (MarketGuardConfig.isUpdateNotificationsEnabled() == enabled) {
                return;
            }
            MarketGuardConfig.setUpdateNotificationsEnabled(enabled);
            MarketGuardConfig.save();
        }
    };

    @Override
    public MarketGuardSettingsApi settings() {
        return SETTINGS;
    }

    @Override
    public CachedValue<BazaarProduct> lookupCachedBazaarProduct(String itemId) {
        BazaarData.LookupResult result = BazaarData.lookupProduct(itemId);
        BazaarProduct value = result.value() == null
                ? null
                : new BazaarProduct(result.value().itemName(), result.value().buy(), result.value().sell());
        return new CachedValue<>(value, result.stale(), result.loading(), result.refreshFailed());
    }

    @Override
    public CachedValue<Double> lookupCachedLowestBin(String itemId) {
        LowestBinData.LookupResult result = LowestBinData.lookupLowestBin(itemId);
        return new CachedValue<>(result.value(), result.stale(), result.loading(), result.refreshFailed());
    }

    @Override
    public CompletableFuture<CachedValue<BazaarProduct>> requestBazaarProduct(String itemId) {
        return BazaarData.refreshAsyncIfNeeded().thenApply(ignored -> lookupCachedBazaarProduct(itemId));
    }

    @Override
    public CompletableFuture<CachedValue<Double>> requestLowestBin(String itemId) {
        return LowestBinData.refreshAsyncIfNeeded().thenApply(ignored -> lookupCachedLowestBin(itemId));
    }

    @Override
    public CachedValue<PlayerData> lookupCachedPlayer(String player, String profileId) {
        return PlayerHud.lookupCachedPlayer(player, profileId);
    }

    @Override
    public CompletableFuture<CachedValue<PlayerData>> requestPlayer(String player, String profileId) {
        return PlayerHud.requestPlayer(player, profileId);
    }
}
