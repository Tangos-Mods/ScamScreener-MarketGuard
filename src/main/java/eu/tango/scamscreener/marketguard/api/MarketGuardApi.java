package eu.tango.scamscreener.marketguard.api;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Public API exposed by MarketGuard for other client-side mods.
 *
 * <p>Obtain it through the Fabric entrypoint {@link #ENTRYPOINT_KEY}:
 * {@code FabricLoader.getInstance().getEntrypoints(MarketGuardApi.ENTRYPOINT_KEY, MarketGuardApi.class)}.</p>
 *
 * <p>The {@code lookupCached...} methods never send an HTTP request and never start a cache refresh.
 * The {@code request...} methods use MarketGuard's shared request and cache instead of creating a
 * second connection, so concurrent mods do not send duplicate requests to the MarketGuard API.</p>
 */
public interface MarketGuardApi {
    String ENTRYPOINT_KEY = "marketguard-api";

    /**
     * Returns the user-facing settings that companion mods such as PackCore may read and change.
     */
    MarketGuardSettingsApi settings();

    CachedValue<BazaarProduct> lookupCachedBazaarProduct(String itemId);

    CachedValue<Double> lookupCachedLowestBin(String itemId);

    CompletableFuture<CachedValue<BazaarProduct>> requestBazaarProduct(String itemId);

    CompletableFuture<CachedValue<Double>> requestLowestBin(String itemId);

    CachedValue<PlayerData> lookupCachedPlayer(String player, String profileId);

    CompletableFuture<CachedValue<PlayerData>> requestPlayer(String player, String profileId);

    /**
     * A cached lookup result. The state flags describe the MarketGuard cache and request that supplied it.
     */
    record CachedValue<T>(T value, boolean stale, boolean loading, boolean refreshFailed) {
        public boolean hasValue() {
            return value != null;
        }
    }

    record BazaarProduct(String itemName, double buy, double sell) {}

    /**
     * Stable player metadata with a defensive copy of the complete MarketGuard API response for optional fields.
     */
    record PlayerData(String uuid, String name, String status, boolean blacklisted, boolean apiStale, JsonObject raw) {
        public PlayerData {
            raw = raw == null ? new JsonObject() : raw.deepCopy();
        }

        @Override
        public JsonObject raw() {
            return raw.deepCopy();
        }
    }
}
