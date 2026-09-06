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

        assertEquals("Price volatility: 7d avg is 20.0% above 30d avg.", warning.text());
        assertFalse(warning.highRisk());
    }

    @Test
    void marksLargeDownwardAuctionMoveAsHighRisk() {
        MarketRiskEvaluator.Warning warning = MarketRiskEvaluator.auctionVolatility(650_000.0, 1_000_000.0);

        assertEquals("Price volatility: 7d avg is 35.0% below 30d avg.", warning.text());
        assertTrue(warning.highRisk());
    }

    @Test
    void ignoresStableOrIncompleteAuctionAverages() {
        assertNull(MarketRiskEvaluator.auctionVolatility(1_100_000.0, 1_000_000.0));
        assertNull(MarketRiskEvaluator.auctionVolatility(1_000_000.0, null));
    }

    @Test
    void combinesSpreadDepthAndTurnoverIntoOneLiquidityWarning() {
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

        assertEquals(
                "Bazaar liquidity risk: 20.0% instant spread, 400 units on thinner book side, 3,000/week on slower side.",
                warning.text()
        );
        assertTrue(warning.highRisk());
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
