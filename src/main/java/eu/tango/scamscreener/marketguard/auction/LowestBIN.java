package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LowestBIN {

    private static final String URL = "https://scamscreener.creepans.net/api/v1/lowestbin";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final Object LOCK = new Object();

    // Cache full lowestbin.json snapshot for 60 seconds.
    private static final long TTL_MS = 60_000;
    private static final long RETRY_DELAY_MS = 10_000;
    private static volatile JsonObject cachedSnapshot;
    private static volatile long cacheExpiresAtMs = 0L;
    private static volatile CompletableFuture<JsonObject> refreshInFlight;
    private static volatile boolean lastRefreshAttemptFailed = false;
    private static volatile long lastRefreshAttemptAtMs = 0L;
    private static volatile boolean refreshFailureNoticeShown = false;

    public record LookupResult(Double value, boolean stale, boolean loading, boolean refreshFailed) {
        public boolean hasValue() {
            return value != null;
        }
    }

    public static double getLowestBIN(String itemId) throws Exception {
        JsonObject snapshot = getSnapshot();
        if (!snapshot.has(itemId)) throw new Exception("Item not found");
        double lowestBin = snapshot.get(itemId).getAsDouble();
        MarketGuard.debug("Lowest BIN lookup hit itemId='{}' value={}", itemId, lowestBin);
        return lowestBin;
    }

    public static LookupResult lookupLowestBIN(String itemId) {
        JsonObject snapshot = cachedSnapshot;
        long now = System.currentTimeMillis();
        boolean fresh = snapshot != null && now < cacheExpiresAtMs;
        boolean stale = snapshot != null && !fresh;
        boolean loading = isRefreshInFlight();
        Double value = readPrice(snapshot, itemId);

        if (value != null) {
            MarketGuard.debug(
                    "Using {} Lowest BIN cache itemId='{}' loading={} refreshFailed={}",
                    fresh ? "fresh" : "stale",
                    itemId,
                    loading,
                    lastRefreshAttemptFailed
            );
            return new LookupResult(value, stale, loading, lastRefreshAttemptFailed);
        }

        MarketGuard.debug(
                "No cached Lowest BIN value for itemId='{}' hasSnapshot={} stale={} loading={} lastRefreshAttemptFailed={}",
                itemId,
                snapshot != null,
                stale,
                loading,
                lastRefreshAttemptFailed
        );
        return new LookupResult(null, stale, loading, lastRefreshAttemptFailed);
    }

    public static void refreshAsyncIfNeeded() {
        long now = System.currentTimeMillis();

        synchronized (LOCK) {
            if (cachedSnapshot != null && now < cacheExpiresAtMs) {
                return;
            }
            if (refreshInFlight != null && !refreshInFlight.isDone()) {
                return;
            }
            if (now - lastRefreshAttemptAtMs < RETRY_DELAY_MS) {
                return;
            }

            lastRefreshAttemptAtMs = now;
            CompletableFuture<JsonObject> refreshFuture = fetchLowestBinSnapshotAsync();
            refreshInFlight = refreshFuture;

            MarketGuard.debug(
                    "Started async Lowest BIN refresh hasSnapshot={} stale={}",
                    cachedSnapshot != null,
                    cachedSnapshot != null && now >= cacheExpiresAtMs
            );

            refreshFuture.whenComplete((snapshot, throwable) -> finishRefresh(refreshFuture, snapshot, throwable));
        }
    }

    private static JsonObject getSnapshot() throws Exception {
        long now = System.currentTimeMillis();
        JsonObject snapshot = cachedSnapshot;
        if (snapshot != null && now < cacheExpiresAtMs) {
            MarketGuard.debug("Using cached Lowest BIN snapshot expiresInMs={}", cacheExpiresAtMs - now);
            return snapshot;
        }

        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (cachedSnapshot != null && now < cacheExpiresAtMs) {
                MarketGuard.debug("Using cached Lowest BIN snapshot after lock expiresInMs={}", cacheExpiresAtMs - now);
                return cachedSnapshot;
            }

            JsonObject fresh = fetchLowestBinSnapshot();
            cachedSnapshot = fresh;
            cacheExpiresAtMs = now + TTL_MS;
            MarketGuard.debug("Cached Lowest BIN snapshot entries={} ttlMs={}", fresh.size(), TTL_MS);
            return fresh;
        }
    }

    private static CompletableFuture<JsonObject> fetchLowestBinSnapshotAsync() {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Lowest BIN snapshot asynchronously from {}", URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long durationMs = System.currentTimeMillis() - startedAt;
                    MarketGuard.debug(
                            "Lowest BIN async response status={} durationMs={} bodyLength={}",
                            response.statusCode(),
                            durationMs,
                            response.body().length()
                    );
                    return JsonParser.parseString(response.body()).getAsJsonObject();
                });
    }

    private static JsonObject fetchLowestBinSnapshot() throws Exception {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Lowest BIN snapshot from {}", URL);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        long durationMs = System.currentTimeMillis() - startedAt;
        MarketGuard.debug(
                "Lowest BIN response status={} durationMs={} bodyLength={}",
                response.statusCode(),
                durationMs,
                response.body().length()
        );
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void finishRefresh(CompletableFuture<JsonObject> refreshFuture, JsonObject snapshot, Throwable throwable) {
        synchronized (LOCK) {
            if (throwable == null) {
                cachedSnapshot = snapshot;
                cacheExpiresAtMs = System.currentTimeMillis() + TTL_MS;
                lastRefreshAttemptFailed = false;
                refreshFailureNoticeShown = false;
                MarketGuard.debug("Async Lowest BIN refresh completed entries={} ttlMs={}", snapshot.size(), TTL_MS);
            } else {
                lastRefreshAttemptFailed = true;
                Throwable cause = rootCause(throwable);
                MarketGuard.LOGGER.warn("Async Lowest BIN refresh failed: {}", cause.getMessage(), cause);
                MarketGuard.debug("Async Lowest BIN refresh failed error='{}'", cause.getMessage());
                notifyRefreshFailureOnce();
            }

            if (refreshInFlight == refreshFuture) {
                refreshInFlight = null;
            }
        }
    }

    private static boolean isRefreshInFlight() {
        CompletableFuture<JsonObject> refreshFuture = refreshInFlight;
        return refreshFuture != null && !refreshFuture.isDone();
    }

    private static Double readPrice(JsonObject snapshot, String itemId) {
        if (snapshot == null || !snapshot.has(itemId)) {
            return null;
        }

        return snapshot.get(itemId).getAsDouble();
    }

    private static void notifyRefreshFailureOnce() {
        if (refreshFailureNoticeShown) {
            return;
        }

        refreshFailureNoticeShown = true;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            MessageBuilder.error(
                    Text.literal("Lowest BIN prices could not be refreshed. MarketGuard will not block AH actions because of missing API data.")
                            .formatted(Formatting.YELLOW),
                    client.player
            );
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
