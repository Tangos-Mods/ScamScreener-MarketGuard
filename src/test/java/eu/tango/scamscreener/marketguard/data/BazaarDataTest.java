package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BazaarDataTest {

    @AfterEach
    void resetState() {
        BazaarData.resetForTests();
    }

    @Test
    void parsesSuccessfulResponse() {
        HttpResponse<String> response = mockResponse(
                200,
                """
                {"products":{"ENCHANTED_BREAD":{"item_name":"Enchanted Bread","buy":12.5,"sell":10.0,"buyVolume":12000,"sellVolume":9000,"buyMovingWeek":81000,"sellMovingWeek":72000}}}
                """
        );

        JsonObject snapshot = BazaarData.parseSnapshot(response);

        assertTrue(snapshot.has("ENCHANTED_BREAD"));
        JsonObject product = snapshot.getAsJsonObject("ENCHANTED_BREAD");
        assertEquals("Enchanted Bread", product.get("item_name").getAsString());
        assertEquals(12.5, product.get("buy").getAsDouble());
        assertEquals(10.0, product.get("sell").getAsDouble());
        assertEquals(12000, product.get("buyVolume").getAsLong());
        assertEquals(9000, product.get("sellVolume").getAsLong());
        assertEquals(81000, product.get("buyMovingWeek").getAsLong());
        assertEquals(72000, product.get("sellMovingWeek").getAsLong());
    }

    @Test
    void rejectsResponseWithoutProductsObject() {
        HttpResponse<String> response = mockResponse(200, "{\"success\":true}");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> BazaarData.parseSnapshot(response));

        assertEquals("Bazaar response did not contain a products object", error.getMessage());
    }

    @Test
    void rejectsHttpErrorResponse() {
        HttpResponse<String> response = mockResponse(503, "{\"error\":\"offline\"}");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> BazaarData.parseSnapshot(response));

        assertEquals("Bazaar request failed with status 503", error.getMessage());
    }

    @Test
    void returnsFreshCachedProduct() throws Exception {
        JsonObject snapshot = new JsonObject();
        JsonObject product = product("Enchanted Bread", 12.5, 10.0);
        product.addProperty("buyVolume", 12_000);
        product.addProperty("sellVolume", 9_000);
        product.addProperty("buyMovingWeek", 81_000);
        product.addProperty("sellMovingWeek", 72_000);
        snapshot.add("ENCHANTED_BREAD", product);

        BazaarData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        BazaarData.LookupResult result = BazaarData.lookupProduct("ENCHANTED_BREAD");

        assertTrue(result.hasValue());
        assertEquals("Enchanted Bread", result.value().itemName());
        assertEquals(12.5, result.value().buy());
        assertEquals(10.0, result.value().sell());
        assertEquals(12_000L, result.value().buyVolume());
        assertEquals(9_000L, result.value().sellVolume());
        assertEquals(81_000L, result.value().buyMovingWeek());
        assertEquals(72_000L, result.value().sellMovingWeek());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void returnsStaleCachedProductAfterFailedRefresh() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("ENCHANTED_BREAD", product("Enchanted Bread", 12.5, 10.0));

        BazaarData.cache().setSnapshotForTests(snapshot, 0L);
        BazaarData.cache().setLastRefreshAttemptFailedForTests(true);

        BazaarData.LookupResult result = BazaarData.lookupProduct("ENCHANTED_BREAD");

        assertTrue(result.hasValue());
        assertTrue(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void reportsPendingRefreshForStaleCache() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("ENCHANTED_BREAD", product("Enchanted Bread", 12.5, 10.0));

        BazaarData.cache().setSnapshotForTests(snapshot, 0L);
        BazaarData.cache().setRefreshInFlightForTests(new CompletableFuture<JsonObject>());

        BazaarData.LookupResult result = BazaarData.lookupProduct("ENCHANTED_BREAD");

        assertTrue(result.hasValue());
        assertTrue(result.stale());
        assertTrue(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void findItemIdByNameUsesNormalizedDisplayName() {
        JsonObject snapshot = new JsonObject();
        snapshot.add("ENCHANTED_BREAD", product("Enchanted Bread", 12.5, 10.0));

        BazaarData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        assertEquals("ENCHANTED_BREAD", BazaarData.findItemIdByName("  enchanted   bread "));
    }

    @Test
    void keepsOptionalMarketDepthEmptyForOlderSnapshots() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("ENCHANTED_BREAD", product("Enchanted Bread", 12.5, 10.0));

        BazaarData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        BazaarData.Product product = BazaarData.lookupProduct("ENCHANTED_BREAD").value();

        assertNull(product.buyVolume());
        assertNull(product.sellVolume());
        assertNull(product.buyMovingWeek());
        assertNull(product.sellMovingWeek());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static JsonObject product(String itemName, double buy, double sell) {
        JsonObject product = new JsonObject();
        product.addProperty("item_name", itemName);
        product.addProperty("buy", buy);
        product.addProperty("sell", sell);
        return product;
    }
}
