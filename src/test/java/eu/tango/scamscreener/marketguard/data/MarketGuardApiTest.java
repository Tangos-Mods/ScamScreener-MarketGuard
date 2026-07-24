package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.api.MarketGuardApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketGuardApiTest {
    @AfterEach
    void resetCaches() {
        BazaarData.resetForTests();
        LowestBinData.resetForTests();
    }

    @Test
    void exposesCachedBazaarDataWithoutARefresh() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = new JsonObject();
        product.addProperty("item_name", "Enchanted Bread");
        product.addProperty("buy", 12.5);
        product.addProperty("sell", 10.0);
        snapshot.add("ENCHANTED_BREAD", product);
        BazaarData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        MarketGuardApi.CachedValue<MarketGuardApi.BazaarProduct> result =
                MarketGuardApi.lookupCachedBazaarProduct("ENCHANTED_BREAD");

        assertTrue(result.hasValue());
        assertEquals("Enchanted Bread", result.value().itemName());
        assertEquals(12.5, result.value().buy());
        assertEquals(10.0, result.value().sell());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
        assertNull(BazaarData.cache().refreshInFlight());
    }

    @Test
    void reportsStaleLowestBinDataWithoutStartingARefresh() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = new JsonObject();
        product.addProperty("price", 123.0);
        snapshot.add("FANCY_LEGGINGS", product);
        LowestBinData.cache().setSnapshotForTests(snapshot, 0L);
        LowestBinData.cache().setRefreshInFlightForTests(new CompletableFuture<>());
        LowestBinData.cache().setLastRefreshAttemptFailedForTests(true);

        MarketGuardApi.CachedValue<Double> result = MarketGuardApi.lookupCachedLowestBin("FANCY_LEGGINGS");

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertTrue(result.stale());
        assertTrue(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void reportsACacheMissWithoutStartingARequest() {
        MarketGuardApi.CachedValue<Double> result = MarketGuardApi.lookupCachedLowestBin("FANCY_LEGGINGS");

        assertFalse(result.hasValue());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
        assertNull(LowestBinData.cache().refreshInFlight());
    }

    @Test
    void asyncLowestBinRequestWaitsForTheSharedRefresh() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = new JsonObject();
        product.addProperty("price", 123.0);
        snapshot.add("FANCY_LEGGINGS", product);
        CompletableFuture<JsonObject> sharedRefresh = new CompletableFuture<>();
        LowestBinData.cache().setSnapshotForTests(snapshot, 0L);
        LowestBinData.cache().setRefreshInFlightForTests(sharedRefresh);

        CompletableFuture<MarketGuardApi.CachedValue<Double>> request =
                MarketGuardApi.requestLowestBin("FANCY_LEGGINGS");

        assertFalse(request.isDone());
        sharedRefresh.complete(snapshot);

        assertEquals(123.0, request.join().value());
    }

    @Test
    void asyncBazaarRequestWaitsForTheSharedRefresh() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = new JsonObject();
        product.addProperty("item_name", "Enchanted Bread");
        product.addProperty("buy", 12.5);
        product.addProperty("sell", 10.0);
        snapshot.add("ENCHANTED_BREAD", product);
        CompletableFuture<JsonObject> sharedRefresh = new CompletableFuture<>();
        BazaarData.cache().setSnapshotForTests(snapshot, 0L);
        BazaarData.cache().setRefreshInFlightForTests(sharedRefresh);

        CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.BazaarProduct>> request =
                MarketGuardApi.requestBazaarProduct("ENCHANTED_BREAD");

        assertFalse(request.isDone());
        sharedRefresh.complete(snapshot);

        assertEquals("Enchanted Bread", request.join().value().itemName());
    }

    @Test
    void asyncRequestReturnsTheCachedFailureStateWithoutRetrying() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = new JsonObject();
        product.addProperty("item_name", "Enchanted Bread");
        product.addProperty("buy", 12.5);
        product.addProperty("sell", 10.0);
        snapshot.add("ENCHANTED_BREAD", product);
        BazaarData.cache().setSnapshotForTests(snapshot, 0L);
        BazaarData.cache().setLastRefreshAttemptFailedForTests(true);
        BazaarData.cache().setLastRefreshAttemptAtMsForTests(System.currentTimeMillis());

        MarketGuardApi.CachedValue<MarketGuardApi.BazaarProduct> result =
                MarketGuardApi.requestBazaarProduct("ENCHANTED_BREAD").join();

        assertTrue(result.hasValue());
        assertTrue(result.stale());
        assertTrue(result.refreshFailed());
        assertNull(BazaarData.cache().refreshInFlight());
    }
}
