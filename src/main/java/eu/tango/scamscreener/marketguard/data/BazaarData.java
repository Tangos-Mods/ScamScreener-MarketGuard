package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.MarketGuard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class BazaarData {
    private static final String URL = "https://scamscreener.creepans.net/api/v1/bazaar";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final SnapshotCache CACHE = new SnapshotCache(60_000, 10_000);

    private BazaarData() {}

    public record Product(String itemName, double buy, double sell) {}

    public record LookupResult(Product value, boolean stale, boolean loading, boolean refreshFailed) {
        public boolean hasValue() {
            return value != null;
        }
    }

    public static Product getProduct(String itemId) throws Exception {
        JsonObject snapshot = getSnapshot();
        Product product = readProduct(snapshot, itemId);
        if (product == null) {
            throw new Exception("Item not found");
        }

        MarketGuard.debug(
                "Bazaar lookup hit itemId='{}' buy={} sell={}",
                itemId,
                product.buy(),
                product.sell()
        );
        return product;
    }

    public static LookupResult lookupProduct(String itemId) {
        SnapshotCache.View cacheView = CACHE.view();
        Product value = readProduct(cacheView.snapshot(), itemId);

        if (value != null) {
            MarketGuard.debug(
                    "Using {} Bazaar cache itemId='{}' loading={} refreshFailed={}",
                    cacheView.stale() ? "stale" : "fresh",
                    itemId,
                    cacheView.loading(),
                    cacheView.refreshFailed()
            );
            return new LookupResult(value, cacheView.stale(), cacheView.loading(), cacheView.refreshFailed());
        }

        MarketGuard.debug(
                "No cached Bazaar value for itemId='{}' hasSnapshot={} stale={} loading={} lastRefreshAttemptFailed={}",
                itemId,
                cacheView.snapshot() != null,
                cacheView.stale(),
                cacheView.loading(),
                cacheView.refreshFailed()
        );
        return new LookupResult(null, cacheView.stale(), cacheView.loading(), cacheView.refreshFailed());
    }

    public static String findItemIdByName(String displayName) {
        return SnapshotDataUtil.findItemIdByName(CACHE.cachedSnapshot(), displayName, BazaarData::readItemName);
    }

    public static void refreshAsyncIfNeeded() {
        CACHE.refreshAsyncIfNeeded("Bazaar", BazaarData::fetchSnapshotAsync, null, null);
    }

    static JsonObject getSnapshot() throws Exception {
        return CACHE.getSnapshot("Bazaar", BazaarData::fetchSnapshot);
    }

    private static CompletableFuture<JsonObject> fetchSnapshotAsync() {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Bazaar snapshot asynchronously from {}", URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("User-Agent", MarketGuard.userAgent())
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    MarketGuard.debug(
                            "Bazaar async response status={} durationMs={} bodyLength={}",
                            response.statusCode(),
                            durationMs,
                            response.body().length()
                    );
                    return parseSnapshot(response);
                });
    }

    private static JsonObject fetchSnapshot() throws Exception {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Bazaar snapshot from {}", URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("User-Agent", MarketGuard.userAgent())
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        long durationMs = System.currentTimeMillis() - startedAt;
        MarketGuard.debug(
                "Bazaar response status={} durationMs={} bodyLength={}",
                response.statusCode(),
                durationMs,
                response.body().length()
        );
        return parseSnapshot(response);
    }

    private static Product readProduct(JsonObject snapshot, String itemId) {
        if (snapshot == null || !snapshot.has(itemId) || !snapshot.get(itemId).isJsonObject()) {
            return null;
        }

        JsonObject product = snapshot.getAsJsonObject(itemId);
        if (!product.has("buy") || !product.has("sell")) {
            return null;
        }

        String itemName = product.has("item_name") ? product.get("item_name").getAsString() : itemId;
        return new Product(itemName, product.get("buy").getAsDouble(), product.get("sell").getAsDouble());
    }

    private static String readItemName(JsonObject product) {
        if (product == null || !product.has("item_name")) {
            return null;
        }

        return product.get("item_name").getAsString();
    }

    static JsonObject parseSnapshot(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bazaar request failed with status " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("products") || !root.get("products").isJsonObject()) {
            throw new IllegalStateException("Bazaar response did not contain a products object");
        }

        return root.getAsJsonObject("products");
    }

    static SnapshotCache cache() {
        return CACHE;
    }

    static void resetForTests() {
        CACHE.reset();
    }
}
