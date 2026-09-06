package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class TradeGuardHudTest {

    @Test
    void usesOnlyTheTwoFourByFourOfferAreas() {
        assertArrayEquals(new int[]{
                0, 1, 2, 3,
                9, 10, 11, 12,
                18, 19, 20, 21,
                27, 28, 29, 30
        }, TradeGuardHud.ownOfferSlots());
        assertArrayEquals(new int[]{
                5, 6, 7, 8,
                14, 15, 16, 17,
                23, 24, 25, 26,
                32, 33, 34, 35
        }, TradeGuardHud.partnerOfferSlots());
    }

    @Test
    void multipliesItemPricesByStackSizeAndWarnsAtBothThresholds() {
        TradeGuardHud.Offer own = offer("OWN_ITEM", 2);
        TradeGuardHud.Offer partner = offer("PARTNER_ITEM", 1);

        TradeGuardHud.Evaluation warning = TradeGuardHud.evaluate(
                own,
                partner,
                itemId -> reliable("OWN_ITEM".equals(itemId) ? 500.0 : 600.0),
                80,
                100L
        );
        TradeGuardHud.Evaluation belowAbsoluteThreshold = TradeGuardHud.evaluate(
                own,
                partner,
                itemId -> reliable("OWN_ITEM".equals(itemId) ? 500.0 : 600.0),
                80,
                500L
        );

        assertEquals(1_000.0, warning.own().total());
        assertEquals(600.0, warning.partner().total());
        assertEquals(-400.0, warning.difference());
        assertEquals(0.4, warning.disadvantagePercentage());
        assertTrue(warning.warning());
        assertFalse(belowAbsoluteThreshold.warning());
    }

    @Test
    void doesNotWarnWhenEitherSideContainsAnUnpricedItem() {
        TradeGuardHud.Evaluation evaluation = TradeGuardHud.evaluate(
                offer("OWN_ITEM", 2),
                new TradeGuardHud.Offer(List.of(new TradeGuardHud.OfferItem("UNKNOWN", 1)), 1),
                itemId -> "OWN_ITEM".equals(itemId) ? reliable(500.0) : missing(),
                80,
                100L
        );

        assertEquals(2, evaluation.partner().unpricedStacks());
        assertFalse(evaluation.warning());
    }

    @Test
    void doesNotWarnFromALowConfidenceAuctionReference() {
        TradeGuardHud.Evaluation evaluation = TradeGuardHud.evaluate(
                offer("OWN_ITEM", 1),
                offer("PARTNER_ITEM", 1),
                itemId -> "OWN_ITEM".equals(itemId) ? unreliable(1_000.0) : reliable(500.0),
                80,
                100L
        );

        assertEquals(1, evaluation.own().lowConfidenceStacks());
        assertFalse(evaluation.warning());
    }

    @Test
    void doesNotWarnFromStaleOrRefreshingPrices() {
        TradeGuardHud.Evaluation stale = TradeGuardHud.evaluate(
                offer("OWN_ITEM", 1),
                offer("PARTNER_ITEM", 1),
                itemId -> new TradeGuardHud.PriceQuote(
                        "OWN_ITEM".equals(itemId) ? 1_000.0 : 500.0,
                        true,
                        true,
                        false,
                        false
                ),
                80,
                100L
        );
        TradeGuardHud.Evaluation refreshing = TradeGuardHud.evaluate(
                offer("OWN_ITEM", 1),
                offer("PARTNER_ITEM", 1),
                itemId -> new TradeGuardHud.PriceQuote(
                        "OWN_ITEM".equals(itemId) ? 1_000.0 : 500.0,
                        true,
                        false,
                        true,
                        false
                ),
                80,
                100L
        );

        assertFalse(stale.warning());
        assertFalse(refreshing.warning());
    }

    @Test
    void prefersBazaarInstantSellBeforeAuctionReferenceData() {
        BazaarData.Product product = new BazaarData.Product("Test", 120.0, 100.0);
        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class);
             MockedStatic<LowestBinData> auction = mockStatic(LowestBinData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("TEST"))
                    .thenReturn(new BazaarData.LookupResult(product, false, false, false));

            TradeGuardHud.PriceQuote quote = TradeGuardHud.lookupPrice("TEST");

            assertEquals(100.0, quote.value());
            assertTrue(quote.reliable());
            auction.verifyNoInteractions();
        }
    }

    @Test
    void fallsBackToTheQualityCheckedAuctionReference() {
        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class);
             MockedStatic<LowestBinData> auction = mockStatic(LowestBinData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("TEST"))
                    .thenReturn(new BazaarData.LookupResult(null, false, false, false));
            auction.when(() -> LowestBinData.lookupLowestBin("TEST"))
                    .thenReturn(new LowestBinData.LookupResult(
                            900.0,
                            1_000.0,
                            1_100.0,
                            false,
                            false,
                            false
                    ));

            TradeGuardHud.PriceQuote quote = TradeGuardHud.lookupPrice("TEST");

            assertEquals(1_000.0, quote.value());
            assertTrue(quote.reliable());
        }
    }

    private static TradeGuardHud.Offer offer(String itemId, int count) {
        return new TradeGuardHud.Offer(List.of(new TradeGuardHud.OfferItem(itemId, count)), 0);
    }

    private static TradeGuardHud.PriceQuote reliable(double value) {
        return new TradeGuardHud.PriceQuote(value, true, false, false, false);
    }

    private static TradeGuardHud.PriceQuote unreliable(double value) {
        return new TradeGuardHud.PriceQuote(value, false, false, false, false);
    }

    private static TradeGuardHud.PriceQuote missing() {
        return new TradeGuardHud.PriceQuote(null, false, false, false, false);
    }
}
