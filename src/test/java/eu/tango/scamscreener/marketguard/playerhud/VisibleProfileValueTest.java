package eu.tango.scamscreener.marketguard.playerhud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibleProfileValueTest {

    @Test
    void sumsBalancesAndOnlyPricedVisibleGear() {
        Map<String, VisibleProfileValue.ItemPrice> prices = Map.of(
                "HELMET", price(1_000_000.0),
                "BELT", price(250_000.0)
        );

        VisibleProfileValue.Estimate estimate = VisibleProfileValue.estimate(
                42_000_000.0,
                1_000_000.0,
                List.of(
                        new VisibleProfileValue.Item("HELMET", 1),
                        new VisibleProfileValue.Item("BELT", 2)
                ),
                prices::get
        );

        assertEquals(44_500_000.0, estimate.value());
        assertEquals(3, estimate.pricedItems());
        assertEquals(3, estimate.visibleItems());
        assertEquals(0, estimate.missingItemPrices());
        assertTrue(estimate.hasValue());
    }

    @Test
    void leavesMissingAndLowQualityPricesOutOfTheEstimate() {
        VisibleProfileValue.Estimate estimate = VisibleProfileValue.estimate(
                5_000_000.0,
                null,
                List.of(
                        new VisibleProfileValue.Item("LOW_QUALITY", 1),
                        new VisibleProfileValue.Item("UNKNOWN", 2)
                ),
                itemId -> "LOW_QUALITY".equals(itemId)
                        ? new VisibleProfileValue.ItemPrice(null, true, false, false, false)
                        : null
        );

        assertEquals(5_000_000.0, estimate.value());
        assertEquals(1, estimate.includedBalances());
        assertEquals(1, estimate.missingBalances());
        assertEquals(3, estimate.missingItemPrices());
        assertEquals(1, estimate.lowQualityItemPrices());
    }

    @Test
    void carriesMarketCacheStateIntoTheEstimate() {
        VisibleProfileValue.Estimate estimate = VisibleProfileValue.estimate(
                null,
                null,
                List.of(new VisibleProfileValue.Item("HELMET", 1)),
                ignored -> new VisibleProfileValue.ItemPrice(1_000_000.0, false, true, true, true)
        );

        assertTrue(estimate.stale());
        assertTrue(estimate.loading());
        assertTrue(estimate.refreshFailed());
        assertTrue(estimate.hasValue());
    }

    @Test
    void reportsNoEstimateWhenNothingVisibleCanBeValued() {
        VisibleProfileValue.Estimate estimate = VisibleProfileValue.estimate(
                Double.NaN,
                -1.0,
                List.of(new VisibleProfileValue.Item(null, 1)),
                ignored -> price(1.0)
        );

        assertFalse(estimate.hasValue());
        assertEquals(2, estimate.missingBalances());
        assertEquals(1, estimate.missingItemPrices());
    }

    private static VisibleProfileValue.ItemPrice price(double value) {
        return new VisibleProfileValue.ItemPrice(value, false, false, false, false);
    }
}
