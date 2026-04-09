package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowestBINLookupTest {

    @AfterEach
    void resetState() throws Exception {
        setField("cachedSnapshot", null);
        setField("cacheExpiresAtMs", 0L);
        setField("refreshInFlight", null);
        setField("lastRefreshAttemptFailed", false);
        setField("lastRefreshAttemptAtMs", 0L);
    }

    @Test
    void returnsFreshCachedValue() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("FANCY_LEGGINGS", 123.0);

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", System.currentTimeMillis() + 60_000L);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void returnsStaleValueAfterFailedRefresh() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("FANCY_LEGGINGS", 123.0);

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", 0L);
        setField("lastRefreshAttemptFailed", true);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertTrue(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void blocksWhileRefreshIsStillPending() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("FANCY_LEGGINGS", 123.0);

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", 0L);
        setField("lastRefreshAttemptFailed", false);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");

        assertFalse(result.hasValue());
        assertTrue(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void reportsFailedRefreshWithoutCache() throws Exception {
        setField("cachedSnapshot", null);
        setField("cacheExpiresAtMs", 0L);
        setField("lastRefreshAttemptFailed", true);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");

        assertFalse(result.hasValue());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    private static void setField(String fieldName, Object value) throws Exception {
        Field field = LowestBIN.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
