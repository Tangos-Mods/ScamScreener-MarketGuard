package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class LowestBinData {
    private static final String URL = "https://scamscreener.creepans.net/api/v2/lowestbin";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final SnapshotCache CACHE = new SnapshotCache(60_000, 10_000);
    private static volatile boolean refreshFailureNoticeShown = false;
    private static volatile String lastBlacklistNoticeKey;

    private LowestBinData() {}

    public record LookupResult(Double value, boolean stale, boolean loading, boolean refreshFailed) {
        public boolean hasValue() {
            return value != null;
        }
    }

    public static double getLowestBin(String itemId) throws Exception {
        JsonObject snapshot = getSnapshot();
        Double lowestBin = readPrice(snapshot, itemId);
        if (lowestBin == null) {
            throw new Exception("Item not found");
        }

        MarketGuard.debug("Lowest BIN lookup hit itemId='{}' value={}", itemId, lowestBin);
        return lowestBin;
    }

    public static LookupResult lookupLowestBin(String itemId) {
        SnapshotCache.View cacheView = CACHE.view();
        Double value = readPrice(cacheView.snapshot(), itemId);

        if (value != null) {
            MarketGuard.debug(
                    "Using {} Lowest BIN cache itemId='{}' loading={} refreshFailed={}",
                    cacheView.stale() ? "stale" : "fresh",
                    itemId,
                    cacheView.loading(),
                    cacheView.refreshFailed()
            );
            return new LookupResult(value, cacheView.stale(), cacheView.loading(), cacheView.refreshFailed());
        }

        MarketGuard.debug(
                "No cached Lowest BIN value for itemId='{}' hasSnapshot={} stale={} loading={} lastRefreshAttemptFailed={}",
                itemId,
                cacheView.snapshot() != null,
                cacheView.stale(),
                cacheView.loading(),
                cacheView.refreshFailed()
        );
        return new LookupResult(null, cacheView.stale(), cacheView.loading(), cacheView.refreshFailed());
    }

    public static String findItemIdByName(String displayName) {
        return SnapshotDataUtil.findItemIdByName(CACHE.cachedSnapshot(), displayName, LowestBinData::readItemName);
    }

    public static void refreshAsyncIfNeeded() {
        CACHE.refreshAsyncIfNeeded(
                "Lowest BIN",
                LowestBinData::fetchLowestBinSnapshotAsync,
                LowestBinData::resetRefreshFailureNotice,
                cause -> notifyRefreshFailureOnce()
        );
    }

    public static void checkBlacklistedAuctioneerAsyncIfNeeded(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            MarketGuard.debug("Skipping blacklisted auctioneer check because itemId was blank");
            return;
        }

        JsonObject snapshot = CACHE.cachedSnapshot();
        if (snapshot != null) {
            notifyBlacklistedAuctioneerIfPresent(snapshot, itemId);
            if (CACHE.hasFreshSnapshotNow()) {
                return;
            }
        }

        refreshAsyncIfNeeded();

        CompletableFuture<JsonObject> refreshFuture = CACHE.refreshInFlight();
        if (refreshFuture == null) {
            return;
        }

        refreshFuture.thenAccept(refreshedSnapshot -> notifyBlacklistedAuctioneerIfPresent(refreshedSnapshot, itemId))
                .exceptionally(throwable -> null);
    }

    public static void resetBlacklistNoticeState() {
        lastBlacklistNoticeKey = null;
    }

    static JsonObject getSnapshot() throws Exception {
        return CACHE.getSnapshot("Lowest BIN", LowestBinData::fetchLowestBinSnapshot);
    }

    private static CompletableFuture<JsonObject> fetchLowestBinSnapshotAsync() {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Lowest BIN snapshot asynchronously from {}", URL);

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
                            "Lowest BIN async response status={} durationMs={} bodyLength={}",
                            response.statusCode(),
                            durationMs,
                            response.body().length()
                    );
                    return parseSnapshot(response);
                });
    }

    private static JsonObject fetchLowestBinSnapshot() throws Exception {
        long startedAt = System.currentTimeMillis();
        MarketGuard.debug("Fetching Lowest BIN snapshot from {}", URL);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("User-Agent", MarketGuard.userAgent())
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
        return parseSnapshot(response);
    }

    private static Double readPrice(JsonObject snapshot, String itemId) {
        JsonObject product = readProduct(snapshot, itemId);
        if (product == null) {
            return null;
        }

        notifyBlacklistedAuctioneerIfPresent(snapshot, itemId);

        if (!product.has("price")) {
            return null;
        }

        return product.get("price").getAsDouble();
    }

    private static JsonObject readProduct(JsonObject snapshot, String itemId) {
        if (snapshot == null || !snapshot.has(itemId) || !snapshot.get(itemId).isJsonObject()) {
            return null;
        }

        return snapshot.getAsJsonObject(itemId);
    }

    private static String readItemName(JsonObject product) {
        if (product == null || !product.has("item_name")) {
            return null;
        }

        return product.get("item_name").getAsString();
    }

    private static String readAuctioneerUuid(JsonObject product) {
        if (product == null || !product.has("auctioneerUuid")) {
            return null;
        }

        return product.get("auctioneerUuid").getAsString();
    }

    static JsonObject parseSnapshot(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Lowest BIN request failed with status " + response.statusCode());
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("products") || !root.get("products").isJsonObject()) {
            throw new IllegalStateException("Lowest BIN response did not contain a products object");
        }

        return root.getAsJsonObject("products");
    }

    private static void notifyBlacklistedAuctioneerIfPresent(JsonObject snapshot, String itemId) {
        JsonObject product = readProduct(snapshot, itemId);
        if (product == null) {
            return;
        }

        String auctioneerUuid = readAuctioneerUuid(product);
        MarketGuard.debug(
                "Checking Lowest BIN auctioneer against ScamScreener blacklist itemId='{}' auctioneerUuid='{}'",
                itemId,
                auctioneerUuid
        );
        String blacklistedPlayerName = ScamScreenerBlacklistCompat.findBlacklistedPlayerName(auctioneerUuid);
        if (blacklistedPlayerName != null) {
            MarketGuard.debug(
                    "Lowest BIN auctioneer matched ScamScreener blacklist itemId='{}' player='{}'",
                    itemId,
                    blacklistedPlayerName
            );
            notifyBlacklistedAuctioneer(itemId, blacklistedPlayerName);
        } else {
            MarketGuard.debug("Lowest BIN auctioneer was not present in ScamScreener blacklist itemId='{}'", itemId);
        }
    }

    private static void notifyRefreshFailureOnce() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        if (refreshFailureNoticeShown) {
            return;
        }

        refreshFailureNoticeShown = true;
        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            MessageBuilder.error(
                    Component.literal("Lowest BIN prices could not be refreshed. MarketGuard will not block AH actions because of missing API data.")
                            .withStyle(ChatFormatting.YELLOW),
                    client.player
            );
        });
    }

    private static void notifyBlacklistedAuctioneer(String itemId, String playerName) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            String noticeKey = itemId + "|" + playerName;
            if (noticeKey.equals(lastBlacklistNoticeKey)) {
                MarketGuard.debug(
                        "Skipping duplicate ScamScreener blacklist notice itemId='{}' player='{}'",
                        itemId,
                        playerName
                );
                return;
            }

            lastBlacklistNoticeKey = noticeKey;
            MessageBuilder.blacklistedPlayer(playerName, client.player);
        });
    }

    private static void resetRefreshFailureNotice() {
        refreshFailureNoticeShown = false;
    }

    static SnapshotCache cache() {
        return CACHE;
    }

    static void resetForTests() {
        CACHE.reset();
        refreshFailureNoticeShown = false;
        lastBlacklistNoticeKey = null;
    }
}
