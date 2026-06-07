package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.MarketGuard;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class SnapshotCache {
    private final Object lock = new Object();
    private final long ttlMs;
    private final long retryDelayMs;

    private volatile JsonObject cachedSnapshot;
    private volatile long cacheExpiresAtMs;
    private volatile CompletableFuture<JsonObject> refreshInFlight;
    private volatile boolean lastRefreshAttemptFailed;
    private volatile long lastRefreshAttemptAtMs;

    SnapshotCache(long ttlMs, long retryDelayMs) {
        this.ttlMs = ttlMs;
        this.retryDelayMs = retryDelayMs;
    }

    record View(JsonObject snapshot, boolean stale, boolean loading, boolean refreshFailed) {}

    @FunctionalInterface
    interface SyncFetcher {
        JsonObject fetch() throws Exception;
    }

    @FunctionalInterface
    interface AsyncFetcher {
        CompletableFuture<JsonObject> fetch();
    }

    View view() {
        JsonObject snapshot = cachedSnapshot;
        long now = System.currentTimeMillis();
        boolean fresh = snapshot != null && now < cacheExpiresAtMs;
        return new View(
                snapshot,
                snapshot != null && !fresh,
                isRefreshInFlight(),
                lastRefreshAttemptFailed
        );
    }

    JsonObject cachedSnapshot() {
        return cachedSnapshot;
    }

    boolean hasFreshSnapshotNow() {
        JsonObject snapshot = cachedSnapshot;
        return snapshot != null && System.currentTimeMillis() < cacheExpiresAtMs;
    }

    CompletableFuture<JsonObject> refreshInFlight() {
        return refreshInFlight;
    }

    JsonObject getSnapshot(String name, SyncFetcher fetcher) throws Exception {
        long now = System.currentTimeMillis();
        JsonObject snapshot = cachedSnapshot;
        if (snapshot != null && now < cacheExpiresAtMs) {
            MarketGuard.debug("Using cached {} snapshot expiresInMs={}", name, cacheExpiresAtMs - now);
            return snapshot;
        }

        boolean shouldFetch = false;
        synchronized (lock) {
            now = System.currentTimeMillis();
            if (cachedSnapshot != null && now < cacheExpiresAtMs) {
                MarketGuard.debug("Using cached {} snapshot after lock expiresInMs={}", name, cacheExpiresAtMs - now);
                return cachedSnapshot;
            }
            shouldFetch = true;
        }

        if (!shouldFetch) {
            return cachedSnapshot;
        }

        JsonObject fresh = fetcher.fetch();
        synchronized (lock) {
            if (cachedSnapshot != null && System.currentTimeMillis() < cacheExpiresAtMs) {
                return cachedSnapshot;
            }

            cachedSnapshot = fresh;
            cacheExpiresAtMs = System.currentTimeMillis() + ttlMs;
            MarketGuard.debug("Cached {} snapshot entries={} ttlMs={}", name, fresh.size(), ttlMs);
            return fresh;
        }
    }

    void refreshAsyncIfNeeded(
            String name,
            AsyncFetcher fetcher,
            Runnable onRefreshSuccess,
            Consumer<Throwable> onRefreshFailure
    ) {
        long now = System.currentTimeMillis();

        synchronized (lock) {
            if (cachedSnapshot != null && now < cacheExpiresAtMs) {
                return;
            }
            if (refreshInFlight != null && !refreshInFlight.isDone()) {
                return;
            }
            if (now - lastRefreshAttemptAtMs < retryDelayMs) {
                return;
            }

            lastRefreshAttemptAtMs = now;
            CompletableFuture<JsonObject> refreshFuture = fetcher.fetch();
            refreshInFlight = refreshFuture;

            MarketGuard.debug(
                    "Started async {} refresh hasSnapshot={} stale={}",
                    name,
                    cachedSnapshot != null,
                    cachedSnapshot != null && now >= cacheExpiresAtMs
            );

            refreshFuture.whenComplete((snapshot, throwable) -> finishRefresh(name, refreshFuture, snapshot, throwable, onRefreshSuccess, onRefreshFailure));
        }
    }

    void reset() {
        synchronized (lock) {
            cachedSnapshot = null;
            cacheExpiresAtMs = 0L;
            refreshInFlight = null;
            lastRefreshAttemptFailed = false;
            lastRefreshAttemptAtMs = 0L;
        }
    }

    void setSnapshotForTests(JsonObject snapshot, long expiresAtMs) {
        synchronized (lock) {
            cachedSnapshot = snapshot;
            cacheExpiresAtMs = expiresAtMs;
        }
    }

    void setRefreshInFlightForTests(CompletableFuture<JsonObject> refreshFuture) {
        synchronized (lock) {
            refreshInFlight = refreshFuture;
        }
    }

    void setLastRefreshAttemptFailedForTests(boolean failed) {
        synchronized (lock) {
            lastRefreshAttemptFailed = failed;
        }
    }

    void setLastRefreshAttemptAtMsForTests(long attemptedAtMs) {
        synchronized (lock) {
            lastRefreshAttemptAtMs = attemptedAtMs;
        }
    }

    private void finishRefresh(
            String name,
            CompletableFuture<JsonObject> refreshFuture,
            JsonObject snapshot,
            Throwable throwable,
            Runnable onRefreshSuccess,
            Consumer<Throwable> onRefreshFailure
    ) {
        Throwable failureCause = null;
        boolean invokeSuccessCallback = false;
        boolean invokeFailureCallback = false;

        synchronized (lock) {
            if (throwable == null) {
                cachedSnapshot = snapshot;
                cacheExpiresAtMs = System.currentTimeMillis() + ttlMs;
                lastRefreshAttemptFailed = false;
                invokeSuccessCallback = onRefreshSuccess != null;
            } else {
                lastRefreshAttemptFailed = true;
                failureCause = SnapshotDataUtil.rootCause(throwable);
                invokeFailureCallback = onRefreshFailure != null;
            }

            if (refreshInFlight == refreshFuture) {
                refreshInFlight = null;
            }
        }

        if (throwable == null) {
            if (invokeSuccessCallback) {
                onRefreshSuccess.run();
            }
            MarketGuard.debug("Async {} refresh completed entries={} ttlMs={}", name, snapshot.size(), ttlMs);
            return;
        }

        MarketGuard.LOGGER.warn("Async {} refresh failed: {}", name, failureCause.getMessage(), failureCause);
        MarketGuard.debug("Async {} refresh failed error='{}'", name, failureCause.getMessage());
        if (invokeFailureCallback) {
            onRefreshFailure.accept(failureCause);
        }
    }

    private boolean isRefreshInFlight() {
        CompletableFuture<JsonObject> refreshFuture = refreshInFlight;
        return refreshFuture != null && !refreshFuture.isDone();
    }
}
