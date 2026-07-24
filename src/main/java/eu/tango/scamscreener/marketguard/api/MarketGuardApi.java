package eu.tango.scamscreener.marketguard.api;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.concurrent.CompletableFuture;

/**
 * Access to MarketGuard's market data for other client-side mods.
 *
 * <p>The {@code lookupCached...} methods never send an HTTP request and never start a cache refresh.
 * The {@code request...} methods use MarketGuard's shared request and cache instead of creating a
 * second connection.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarketGuardApi {
    public static CachedValue<BazaarProduct> lookupCachedBazaarProduct(String itemId) {
        BazaarData.LookupResult result = BazaarData.lookupProduct(itemId);
        BazaarProduct value = result.value() == null
                ? null
                : new BazaarProduct(result.value().itemName(), result.value().buy(), result.value().sell());
        return new CachedValue<>(value, result.stale(), result.loading(), result.refreshFailed());
    }

    public static CachedValue<Double> lookupCachedLowestBin(String itemId) {
        LowestBinData.LookupResult result = LowestBinData.lookupLowestBin(itemId);
        return new CachedValue<>(result.value(), result.stale(), result.loading(), result.refreshFailed());
    }

    public static CompletableFuture<CachedValue<BazaarProduct>> requestBazaarProduct(String itemId) {
        return BazaarData.refreshAsyncIfNeeded().thenApply(ignored -> lookupCachedBazaarProduct(itemId));
    }

    public static CompletableFuture<CachedValue<Double>> requestLowestBin(String itemId) {
        return LowestBinData.refreshAsyncIfNeeded().thenApply(ignored -> lookupCachedLowestBin(itemId));
    }

    public static CachedValue<PlayerData> lookupCachedPlayer(String player, String profileId) {
        return PlayerHud.lookupCachedPlayer(player, profileId);
    }

    public static CompletableFuture<CachedValue<PlayerData>> requestPlayer(String player, String profileId) {
        return PlayerHud.requestPlayer(player, profileId);
    }

    /**
     * A cached lookup result. The state flags describe the MarketGuard cache and request that supplied it.
     */
    public record CachedValue<T>(T value, boolean stale, boolean loading, boolean refreshFailed) {
        public boolean hasValue() {
            return value != null;
        }
    }

    public record BazaarProduct(String itemName, double buy, double sell) {}

    /**
     * Stable player metadata with a defensive copy of the complete MarketGuard API response for optional fields.
     */
    public record PlayerData(String uuid, String name, String status, boolean blacklisted, boolean apiStale, JsonObject raw) {
        public PlayerData {
            raw = raw == null ? new JsonObject() : raw.deepCopy();
        }

        @Override
        public JsonObject raw() {
            return raw.deepCopy();
        }
    }
}
