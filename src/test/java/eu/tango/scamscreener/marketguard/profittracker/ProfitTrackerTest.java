package eu.tango.scamscreener.marketguard.profittracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitTrackerTest {

    @AfterEach
    void resetTracker() {
        ProfitTracker.resetForTests();
    }

    @Test
    void bazaarProfitUsesTrackedPurchaseCost(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_BUY, "ENCHANTED_BREAD", "Enchanted Bread", 64, 12_800.0
        );
        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_SELL, "ENCHANTED_BREAD", "Enchanted Bread", 64, 16_000.0
        );

        assertEquals(3_200.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void resetAllClearsRuntimeAndPersistedProfitData(@TempDir Path tempDir) {
        Path storePath = tempDir.resolve("profit_tracker.json");
        ProfitTracker.setStorePathForTests(storePath);
        ProfitTracker.setStateForTests(new ProfitTrackerState());
        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_SELL, "ENCHANTED_BREAD", "Enchanted Bread", 1, 5_000.0
        );

        assertTrue(ProfitTracker.resetAll());
        assertEquals(0.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
        assertTrue(ProfitTrackerStore.load(storePath).profiles.isEmpty());
    }

    @Test
    void minionPayoutRecordsOnlyTheConfirmedHeldCoins(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberMinionPayoutForTests("orange", 12_345.0);

        assertFalse(ProfitTracker.tryHandleMinionPayout("orange", "You received 12,344 coins!"));
        assertTrue(ProfitTracker.tryHandleMinionPayout("orange", "You received 12,345 coins!"));
        assertEquals(12_345.0, ProfitTracker.getMinionAllTimeProfit("orange"));
        assertFalse(ProfitTracker.tryHandleMinionPayout("orange", "You received 12,345 coins!"));
    }

    @Test
    void personalAndCoopBankInterestAreCombined(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleInterest(
                "orange",
                "Since you've been away you earned 12,345 coins as interest in your personal bank account!"
        ));
        assertTrue(ProfitTracker.tryHandleInterest(
                "orange",
                "You have just received 6,789.5 coins as interest in your co-op bank account!"
        ));
        assertFalse(ProfitTracker.tryHandleInterest("orange", "You received 100 coins!"));
        assertEquals(19_134.5, ProfitTracker.getInterestAllTimeProfit("orange"));
    }

    @Test
    void allowanceMessagesAreTrackedSeparately(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleAllowance("orange", "ALLOWANCE! You earned 50,000 coins!"));
        assertFalse(ProfitTracker.tryHandleAllowance("orange", "You earned 50,000 coins!"));
        assertEquals(50_000.0, ProfitTracker.getAllowanceAllTimeProfit("orange"));
    }

    @Test
    void bazaarClaimUsesTheNetCoinsShownByHypixel(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_BUY, "IRON_INGOT", "Iron Ingot", 61_294, 245_176.0
        );

        assertTrue(ProfitTracker.tryHandleBazaarClaimedSale(
                "orange",
                "[Bazaar] Claimed 291,269 coins from selling 61,294x Iron Ingot at 4.8 each!"
        ));
        assertEquals(46_093.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void bazaarClaimedSaleMessagesFromScreenshotsRecordBothExactPayouts(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_BUY, null, "Redstone Dust", 3_328, 20_000.0
        );
        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_BUY, null, "Refined Diamond", 7, 5_000_000.0
        );

        assertTrue(ProfitTracker.tryHandleBazaarClaimedSale(
                "orange",
                "[Bazaar] Claimed 28,025 coins from selling 3,328x Redstone Dust at 8.5 each!"
        ));
        assertTrue(ProfitTracker.tryHandleBazaarClaimedSale(
                "orange",
                "[Bazaar] Claimed 5,543,990 coins from selling 7x Refined Diamond at 799,998 each!"
        ));

        assertEquals(552_015.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void bazaarBuyOrderSetupImmediatelyDeductsItsReservedCoins(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleBazaarBuyOrderSetup(
                "orange",
                "[Bazaar] Buy Order Setup! 71,680x Gold Ingot for 64,512 coins."
        ));
        assertEquals(-64_512.0, ProfitTracker.getBazaarAllTimeProfit("orange"));

        assertTrue(ProfitTracker.tryHandleBazaarClaimedBuy(
                "orange",
                "Bazaar! Claimed 71,680x Gold Ingot worth 64,512 coins bought for 0.9 each!"
        ));
        assertEquals(-64_512.0, ProfitTracker.getBazaarAllTimeProfit("orange"));

        assertTrue(ProfitTracker.tryHandleBazaarClaimedSale(
                "orange",
                "[Bazaar] Claimed 71,680 coins from selling 71,680x Gold Ingot at 1 each!"
        ));
        assertEquals(7_168.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void cancelledBuyOrderCreditsItsRefundOnlyOnceAndKeepsItsClaimCost(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleBazaarBuyOrderSetup(
                "orange",
                "[Bazaar] Buy Order Setup! 10x Gold Ingot for 100 coins."
        ));
        assertTrue(ProfitTracker.tryHandleBazaarBuyOrderCancellation(
                "orange",
                "[Bazaar] Cancelled! Refunded 60 coins from cancelling Buy Order!"
        ));
        assertEquals(-40.0, ProfitTracker.getBazaarAllTimeProfit("orange"));

        assertTrue(ProfitTracker.tryHandleBazaarBuyOrderCancellation(
                "orange",
                "[Bazaar] Cancelled! Refunded 60 coins from cancelling Buy Order!"
        ));
        assertEquals(-40.0, ProfitTracker.getBazaarAllTimeProfit("orange"));

        assertTrue(ProfitTracker.tryHandleBazaarClaimedBuy(
                "orange",
                "Bazaar! Claimed 4x Gold Ingot worth 40 coins bought for 10 each!"
        ));
        assertEquals(-40.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void cancelledBuyOrderCreditsAnUntrackedRefundAndRejectsOtherMessages(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleBazaarBuyOrderCancellation(
                "orange",
                "[Bazaar] Cancelled! Refunded 1,234.5 coins from cancelling Buy Order!"
        ));
        assertEquals(1_234.5, ProfitTracker.getBazaarAllTimeProfit("orange"));

        assertFalse(ProfitTracker.tryHandleBazaarBuyOrderCancellation(
                "orange",
                "[Bazaar] Cancelled! Refunded 1,234.5 coins from cancelling Sell Offer!"
        ));
        assertFalse(ProfitTracker.tryHandleBazaarBuyOrderCancellation(
                "orange",
                "[Bazaar] Refunded 1,234.5 coins from cancelling Buy Order!"
        ));
        assertEquals(1_234.5, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void bazaarInstantSellAddsQuantityTimesTheShownUnitPrice(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());
        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_BUY, null, "Gold Ingot", 96, 7_680.0
        );

        assertTrue(ProfitTracker.tryHandleBazaarInstantTrade(
                "orange",
                "[Bazaar] Sold 96x Gold Ingot for 96.0 coins!",
                BazaarTradeKind.INSTANT_SELL
        ));

        assertEquals(1_536.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void bazaarInstantBuyDeductsQuantityTimesTheShownUnitPrice(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleBazaarInstantTrade(
                "orange",
                "[Bazaar] Bought 1x Cobblestone for 2.6 coins!",
                BazaarTradeKind.INSTANT_BUY
        ));
        assertTrue(ProfitTracker.tryHandleBazaarInstantTrade(
                "orange",
                "[Bazaar] Bought 64x Cobblestone for 166.4 coins!",
                BazaarTradeKind.INSTANT_BUY
        ));

        assertEquals(-10_652.2, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void skyBlockWelcomeEnablesTheSessionUntilDisconnect() {
        assertTrue(ProfitTracker.tryStartSkyBlockSession("Welcome to Hypixel SkyBlock!"));
        assertTrue(ProfitTracker.isSkyBlockSessionActive());

        ProfitTracker.clearEphemeralState();

        assertFalse(ProfitTracker.isSkyBlockSessionActive());
    }

    @Test
    void untrackedBazaarSaleUsesZeroCostBasis(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_SELL, "ENCHANTED_BREAD", "Enchanted Bread", 10, 900.0
        );

        assertEquals(900.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void untrackedBazaarClaimedSaleCreditsTheExactPayout(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        assertTrue(ProfitTracker.tryHandleBazaarClaimedSale(
                "orange",
                "[Bazaar] Claimed 46,787 coins from selling 5,560x Redstone Dust at 8.5 each!"
        ));

        assertEquals(46_787.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void bazaarBuyOrderCreatesAPersistentPurchaseLot(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());
        ProfitTracker.rememberBazaarOrder(
                "orange", BazaarTradeKind.BUY_ORDER, "ENCHANTED_BREAD", "Enchanted Bread", 64, 12_800.0
        );

        assertTrue(ProfitTracker.tryHandleBazaarOrderFill(
                "orange", "Your Buy Order for 64x Enchanted Bread was completely filled!"
        ));
        assertTrue(ProfitTracker.tryHandleBazaarClaimedBuy(
                "orange", "Bazaar! Claimed 64x Enchanted Bread worth 12,800 coins bought for 200 each!"
        ));
        assertEquals(-12_800.0, ProfitTracker.getBazaarAllTimeProfit("orange"));

        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_SELL, "ENCHANTED_BREAD", "Enchanted Bread", 64, 16_000.0
        );
        assertEquals(3_200.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void claimAllCoinsAtSlot32UsesTheActualBazaarPayout(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());
        ProfitTracker.rememberBazaarOrder(
                "orange", BazaarTradeKind.BUY_ORDER, "IRON_INGOT", "Iron Ingot", 4_114, 16_456.0
        );
        assertTrue(ProfitTracker.tryHandleBazaarOrderFill(
                "orange", "Your Buy Order for 4,114x Iron Ingot was completely filled!"
        ));
        assertTrue(ProfitTracker.tryHandleBazaarClaimedBuy(
                "orange", "Bazaar! Claimed 4,114x Iron Ingot worth 16,456 coins bought for 4 each!"
        ));
        ProfitTracker.rememberBazaarOrder(
                "orange", BazaarTradeKind.SELL_ORDER, "IRON_INGOT", "Iron Ingot", 4_114, 19_766.0
        );
        assertTrue(ProfitTracker.tryHandleBazaarOrderFill(
                "orange", "Your Sell Offer for 4,114x Iron Ingot was completely filled!"
        ));

        assertTrue(BazaarSlots.isClaimAllCoins(32, "Claim All Coins"));
        assertTrue(ProfitTracker.claimBazaarCoinsForTests("orange", 19_550.0));
        assertEquals(3_094.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
    }

    @Test
    void readsEachBazaarOrderSlotBeforeItsClaim() {
        ProfitTracker.BazaarOrderSlot order = ProfitTracker.parseBazaarOrderSlot(
                "SELL Redstone Dust",
                List.of(
                        "Offer amount: 71,680x",
                        "Filled: 17.8k/71.7k (24.8%)",
                        "Price per unit: 8.5 coins"
                )
        );

        assertEquals(BazaarTradeKind.SELL_ORDER, order.kind());
        assertEquals("Redstone Dust", order.itemName());
        assertEquals(71_680, order.offerAmount());
        assertEquals(17_800, order.filledAmount());
        assertEquals(8.5, order.unitPrice());
    }

    @Test
    void auctionProfitUsesPurchaseAndSalePrices(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberAuctionPurchaseForTests("orange", "FANCY_LEGGINGS", "Fancy Leggings", 750_000.0);
        assertTrue(ProfitTracker.confirmAuctionPurchaseByItemId("orange", "FANCY_LEGGINGS", 750_000.0));
        assertEquals(0.0, ProfitTracker.getAuctionHouseAllTimeProfit("orange"));

        ProfitTracker.rememberAuctionListingForTests("orange", "FANCY_LEGGINGS", "Fancy Leggings", 950_000.0);
        assertTrue(ProfitTracker.confirmAuctionSaleByItemId("orange", "FANCY_LEGGINGS", 950_000.0));
        assertEquals(200_000.0, ProfitTracker.getAuctionHouseAllTimeProfit("orange"));
    }

    @Test
    void auctionCollectedSaleMessageRecordsTheExactPayout(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberAuctionPurchaseForTests(
                "orange", "EASTER_EGG_MINION_SKIN", "Easter Egg Minion Skin", 250_000.0
        );
        assertTrue(ProfitTracker.confirmAuctionPurchaseByItemId("orange", "EASTER_EGG_MINION_SKIN", 250_000.0));
        ProfitTracker.rememberAuctionListingForTests(
                "orange", "EASTER_EGG_MINION_SKIN", "Easter Egg Minion Skin", 300_000.0
        );

        assertTrue(ProfitTracker.tryHandleAuctionSale(
                "orange",
                "You collected 300,000 coins from selling Easter Egg Minion Skin in an auction!"
        ));
        assertEquals(50_000.0, ProfitTracker.getAuctionHouseAllTimeProfit("orange"));
    }

    @Test
    void auctionCollectedSaleMessageAllowsTheOptionalBuyer(@TempDir Path tempDir) {
        ProfitTracker.setStorePathForTests(tempDir.resolve("profit_tracker.json"));
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.rememberAuctionPurchaseForTests(
                "orange", "EASTER_EGG_MINION_SKIN", "Easter Egg Minion Skin", 250_000.0
        );
        assertTrue(ProfitTracker.confirmAuctionPurchaseByItemId("orange", "EASTER_EGG_MINION_SKIN", 250_000.0));
        ProfitTracker.rememberAuctionListingForTests(
                "orange", "EASTER_EGG_MINION_SKIN", "Easter Egg Minion Skin", 300_000.0
        );

        assertTrue(ProfitTracker.tryHandleAuctionSale(
                "orange",
                "You collected 300,000 coins from selling Easter Egg Minion Skin to [VIP+] Glamourian in an auction!"
        ));
        assertEquals(50_000.0, ProfitTracker.getAuctionHouseAllTimeProfit("orange"));
    }

    @Test
    void keepsProfitsAndPurchaseLotsSeparateForEachProfile(@TempDir Path tempDir) {
        Path storePath = tempDir.resolve("profit_tracker.json");
        ProfitTracker.setStorePathForTests(storePath);
        ProfitTracker.setStateForTests(new ProfitTrackerState());

        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_BUY, "ENCHANTED_BREAD", "Enchanted Bread", 10, 900.0
        );
        ProfitTracker.recordBazaarInstantTrade(
                "orange", BazaarTradeKind.INSTANT_SELL, "ENCHANTED_BREAD", "Enchanted Bread", 10, 1_000.0
        );
        ProfitTracker.recordBazaarInstantTrade(
                "raspberry", BazaarTradeKind.INSTANT_BUY, "ENCHANTED_BREAD", "Enchanted Bread", 10, 900.0
        );

        assertEquals(100.0, ProfitTracker.getBazaarAllTimeProfit("orange"));
        assertEquals(-900.0, ProfitTracker.getBazaarAllTimeProfit("raspberry"));

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);
        assertEquals(100.0, loaded.getProfile("orange").bazaarAllTimeProfit);
        assertEquals(-900.0, loaded.getProfile("raspberry").bazaarAllTimeProfit);
        assertEquals(1, loaded.getProfile("raspberry").trackedBazaarPositions.size());
        assertEquals(900.0, loaded.getProfile("raspberry").trackedBazaarPositions.getFirst().remainingCost);
    }
}
