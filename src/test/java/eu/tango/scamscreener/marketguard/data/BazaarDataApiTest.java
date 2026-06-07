package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("live-api")
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class BazaarDataApiTest {

    @Test
    void endpointReturnsNonEmptySnapshot() throws Exception {
        JsonObject snapshot = BazaarData.getSnapshot();

        assertTrue(snapshot.size() > 0, "bazaar snapshot should not be empty");
        assertTrue(snapshot.has("ABSOLUTE_ENDER_PEARL"), "snapshot should contain a known bazaar item");
        assertTrue(snapshot.getAsJsonObject("ABSOLUTE_ENDER_PEARL").has("buy"), "snapshot entry should contain a buy price");
        assertTrue(snapshot.getAsJsonObject("ABSOLUTE_ENDER_PEARL").has("sell"), "snapshot entry should contain a sell price");
    }

    @Test
    void returnsProductForKnownItem() throws Exception {
        BazaarData.Product product = BazaarData.getProduct("ABSOLUTE_ENDER_PEARL");

        assertTrue(product.buy() > 0.0, "known bazaar item should return a positive buy price");
        assertTrue(product.sell() > 0.0, "known bazaar item should return a positive sell price");
    }
}
