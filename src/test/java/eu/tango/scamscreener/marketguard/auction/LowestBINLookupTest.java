package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class LowestBINLookupTest {

    @AfterEach
    void resetState() throws Exception {
        setField("cachedSnapshot", null);
        setField("cacheExpiresAtMs", 0L);
        setField("refreshInFlight", null);
        setField("lastRefreshAttemptFailed", false);
        setField("lastRefreshAttemptAtMs", 0L);
        setField("refreshFailureNoticeShown", false);
    }

    @Test
    void returnsFreshCachedValue() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, "57ad19ca639f412daee5765f87874e35"));

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", System.currentTimeMillis() + 60_000L);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn(null);
            result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    @Test
    void returnsStaleValueAfterFailedRefresh() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, "57ad19ca639f412daee5765f87874e35"));

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", 0L);
        setField("lastRefreshAttemptFailed", true);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn(null);
            result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertTrue(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void returnsStaleCachedValueWhileRefreshIsStillPending() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, "57ad19ca639f412daee5765f87874e35"));

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", 0L);
        setField("refreshInFlight", new java.util.concurrent.CompletableFuture<JsonObject>());
        setField("lastRefreshAttemptFailed", false);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn(null);
            result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertTrue(result.stale());
        assertTrue(result.loading());
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

    @Test
    void stillReturnsLowestBinWhenAuctioneerIsBlacklisted() throws Exception {
        JsonObject snapshot = new JsonObject();
        snapshot.add("FANCY_LEGGINGS", product(123.0, "57ad19ca639f412daee5765f87874e35"));

        setField("cachedSnapshot", snapshot);
        setField("cacheExpiresAtMs", System.currentTimeMillis() + 60_000L);
        setField("lastRefreshAttemptAtMs", System.currentTimeMillis());

        LowestBIN.LookupResult result;
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName("57ad19ca639f412daee5765f87874e35"))
                    .thenReturn("Scammer");
            result = LowestBIN.lookupLowestBIN("FANCY_LEGGINGS");
        }

        assertTrue(result.hasValue());
        assertEquals(123.0, result.value());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());
    }

    private static JsonObject product(double price, String auctioneerUuid) {
        JsonObject product = new JsonObject();
        product.addProperty("price", price);
        product.addProperty("auctioneerUuid", auctioneerUuid);
        return product;
    }

    private static void setField(String fieldName, Object value) throws Exception {
        Field field = LowestBIN.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
