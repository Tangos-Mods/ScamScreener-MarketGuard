package eu.tango.scamscreener.marketguard.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketRiskEvaluatorTest {

    @Test
    void warnsWhenShortAndLongAuctionAveragesDiverge() {
        MarketRiskEvaluator.Warning warning = MarketRiskEvaluator.auctionVolatility(1_200_000.0, 1_000_000.0);

        assertEquals("Price trend: rising 20% vs. last month", warning.text());
        assertFalse(warning.highRisk());
    }

    @Test
    void marksLargeDownwardAuctionMoveAsHighRisk() {
        MarketRiskEvaluator.Warning warning = MarketRiskEvaluator.auctionVolatility(650_000.0, 1_000_000.0);

        assertEquals("Price trend: falling 35% vs. last month", warning.text());
        assertTrue(warning.highRisk());
    }

    @Test
    void ignoresStableOrIncompleteAuctionAverages() {
        assertNull(MarketRiskEvaluator.auctionVolatility(1_100_000.0, 1_000_000.0));
        assertNull(MarketRiskEvaluator.auctionVolatility(1_000_000.0, null));
    }

    @Test
    void warnsWhenBazaarBookIsThinAndSpreadIsWide() {
        BazaarData.Product product = new BazaarData.Product(
                "Thin Item",
                100.0,
                80.0,
                400L,
                800L,
                3_000L,
                8_000L
        );

        MarketRiskEvaluator.Warning warning = MarketRiskEvaluator.bazaarLiquidity(product);

        assertEquals("Hard to resell on the Bazaar", warning.text());
        assertTrue(warning.highRisk());
    }

    @Test
    void mildSpreadIsAWarningButNotHighRisk() {
        MarketRiskEvaluator.Warning warning = MarketRiskEvaluator.bazaarLiquidity(new BazaarData.Product(
                "Wide Item",
                100.0,
                92.0,
                50_000L,
                60_000L,
                500_000L,
                600_000L
        ));

        assertEquals("Hard to resell on the Bazaar", warning.text());
        assertFalse(warning.highRisk());
    }

    @Test
    void ignoresLiquidBazaarProductAndMissingDepth() {
        assertNull(MarketRiskEvaluator.bazaarLiquidity(new BazaarData.Product(
                "Liquid Item",
                100.0,
                99.0,
                50_000L,
                60_000L,
                500_000L,
                600_000L
        )));
        assertNull(MarketRiskEvaluator.bazaarLiquidity(new BazaarData.Product("Legacy Item", 100.0, 99.0)));
    }
}
