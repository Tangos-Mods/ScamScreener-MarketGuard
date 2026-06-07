package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                {"products":{"ENCHANTED_BREAD":{"item_name":"Enchanted Bread","buy":12.5,"sell":10.0}}}
                """
        );

        JsonObject snapshot = BazaarData.parseSnapshot(response);

        assertTrue(snapshot.has("ENCHANTED_BREAD"));
        JsonObject product = snapshot.getAsJsonObject("ENCHANTED_BREAD");
        assertEquals("Enchanted Bread", product.get("item_name").getAsString());
        assertEquals(12.5, product.get("buy").getAsDouble());
        assertEquals(10.0, product.get("sell").getAsDouble());
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
        snapshot.add("ENCHANTED_BREAD", product("Enchanted Bread", 12.5, 10.0));

        BazaarData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        BazaarData.LookupResult result = BazaarData.lookupProduct("ENCHANTED_BREAD");

        assertTrue(result.hasValue());
        assertEquals("Enchanted Bread", result.value().itemName());
        assertEquals(12.5, result.value().buy());
        assertEquals(10.0, result.value().sell());
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
