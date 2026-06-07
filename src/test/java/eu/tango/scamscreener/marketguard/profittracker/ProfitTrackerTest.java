package eu.tango.scamscreener.marketguard.profittracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitTrackerTest {

    @AfterEach
    void resetTracker() {
        ProfitTracker.resetForTests();
    }

    @Test
    void bazaarOrderFillUsesFrozenCachePrice(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberBazaarOrder(
                "orange",
                BazaarTradeKind.BUY_ORDER,
                "ENCHANTED_BREAD",
                "Enchanted Bread",
                64,
                12_800.0,
                250.0
        );

        assertTrue(ProfitTracker.confirmBazaarFill("orange", BazaarTradeKind.BUY_ORDER, "ENCHANTED_BREAD"));
        assertEquals(3_200.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void instantBazaarSellCanReduceProfit(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.recordBazaarInstantTrade(
                "orange",
                BazaarTradeKind.INSTANT_SELL,
                10,
                900.0,
                100.0
        );

        assertEquals(-100.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void auctionSaleUsesFrozenLowestBin(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberAuctionListingForTests(
                "orange",
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                950_000.0,
                800_000.0
        );

        assertTrue(ProfitTracker.confirmAuctionSaleByItemId("orange", "FANCY_LEGGINGS", 950_000.0));
        assertEquals(150_000.0, ProfitTracker.getAuctionHouseAllTimeProfit("orange"));
    }

    @Test
    void auctionPurchaseUsesFrozenLowestBin(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberAuctionPurchaseForTests(
                "orange",
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                750_000.0,
                800_000.0
        );

        assertTrue(ProfitTracker.confirmAuctionPurchaseByItemId("orange", "FANCY_LEGGINGS", 750_000.0));
        assertEquals(50_000.0, ProfitTracker.getAuctionHouseAllTimeProfit("orange"));
    }
}
