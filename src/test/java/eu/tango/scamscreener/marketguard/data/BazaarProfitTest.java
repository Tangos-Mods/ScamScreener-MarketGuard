package eu.tango.scamscreener.marketguard.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProfitTest {

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
