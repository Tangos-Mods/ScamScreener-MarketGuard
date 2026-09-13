package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProfitTest {

    @AfterEach
    void resetState() {
        BazaarData.resetForTests();
    }

    @Test
    void sumsInstantSellValueForKnownStacksOnly() {
        BazaarProfit.Summary summary = BazaarProfit.summarize(List.of(
                new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", 64),
                new BazaarProfit.Item("UNKNOWN", "Unknown", 3)
        ), this::priceFor);

        assertEquals(320.0, summary.total());
        assertEquals(1, summary.pricedStacks());
        assertEquals(1, summary.missingStacks());
        assertFalse(summary.loading());
        assertTrue(summary.stale());
    }

    @Test
    void valuesOnlyTheObservedInventoryChangeAtCurrentPrices() {
        BazaarProfit.ValueDelta delta = BazaarProfit.valueDelta(
                List.of(new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", 64)),
                List.of(new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", 74)),
                this::priceFor
        );

        assertEquals(50.0, delta.total());
        assertEquals(0, delta.missingItems());
        assertTrue(delta.stale());
    }

    @Test
    void resolvesStacksWithoutSkyblockIdThroughBazaarItemNames() {
        JsonObject snapshot = new JsonObject();
        JsonObject product = new JsonObject();
        product.addProperty("item_name", "Gold Ingot");
        product.addProperty("buy", 6.0);
        product.addProperty("sell", 5.0);
        snapshot.add("GOLD_INGOT", product);
        BazaarData.cache().setSnapshotForTests(snapshot, System.currentTimeMillis() + 60_000L);

        BazaarProfit.Summary summary = BazaarProfit.summarize(List.of(
                new BazaarProfit.Item(null, "Gold  Ingot", 10),
                new BazaarProfit.Item(null, "Unknown", 3)
        ), this::priceFor);
        BazaarProfit.ValueDelta delta = BazaarProfit.valueDelta(
                List.of(new BazaarProfit.Item(null, "Gold Ingot", 4)),
                List.of(new BazaarProfit.Item(null, "gold ingot", 10)),
                this::priceFor
        );

        assertEquals(50.0, summary.total());
        assertEquals(1, summary.pricedStacks());
        assertEquals(1, summary.missingStacks());
        assertEquals(30.0, delta.total());
        assertEquals(0, delta.missingItems());
    }

    private BazaarData.LookupResult priceFor(String itemId) {
        if (!"GOLD_INGOT".equals(itemId)) {
            return new BazaarData.LookupResult(null, false, false, false);
        }
        return new BazaarData.LookupResult(
                new BazaarData.Product("Gold Ingot", 6.0, 5.0),
                true,
                false,
                false
        );
    }
}
