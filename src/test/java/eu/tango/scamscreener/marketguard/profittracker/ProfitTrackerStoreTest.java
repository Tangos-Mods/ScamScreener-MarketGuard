package eu.tango.scamscreener.marketguard.profittracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitTrackerStoreTest {

    @Test
    void savesAndLoadsProfilesWithPendingEntries(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        ProfitTrackerState state = new ProfitTrackerState();
        ProfileProfitState profile = state.getOrCreateProfile("orange");
        profile.bazaarAllTimeProfit = 123.45;
        profile.auctionHouseAllTimeProfit = 678.9;
        profile.minionAllTimeProfit = 222.0;
        profile.interestAllTimeProfit = 333.0;
        profile.allowanceAllTimeProfit = 444.0;
        profile.pendingBazaarOrders.add(new PendingBazaarOrder(
                BazaarTradeKind.BUY_ORDER,
                "ENCHANTED_BREAD",
                "Enchanted Bread",
                64,
                12_800.0,
                100L
        ));
        profile.pendingAuctionListings.add(new PendingAuctionListing(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                999_999.0,
                200L
        ));
        profile.trackedBazaarPositions.add(new TrackedBazaarPosition(
                "ENCHANTED_BREAD",
                "Enchanted Bread",
                32,
                6_400.0,
                300L
        ));
        profile.trackedAuctionPositions.add(new TrackedAuctionPosition(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                800_000.0,
                400L
        ));

        assertTrue(ProfitTrackerStore.save(storePath, state));

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);
        ProfileProfitState loadedProfile = loaded.getProfile("orange");

        assertNotNull(loadedProfile);
        assertEquals(123.45, loadedProfile.bazaarAllTimeProfit);
        assertEquals(678.9, loadedProfile.auctionHouseAllTimeProfit);
        assertEquals(222.0, loadedProfile.minionAllTimeProfit);
        assertEquals(333.0, loadedProfile.interestAllTimeProfit);
        assertEquals(444.0, loadedProfile.allowanceAllTimeProfit);
        assertEquals(1, loadedProfile.pendingBazaarOrders.size());
        assertEquals("ENCHANTED_BREAD", loadedProfile.pendingBazaarOrders.getFirst().itemId);
        assertEquals(1, loadedProfile.pendingAuctionListings.size());
        assertEquals("FANCY_LEGGINGS", loadedProfile.pendingAuctionListings.getFirst().itemId);
        assertEquals(1, loadedProfile.trackedBazaarPositions.size());
        assertEquals(6_400.0, loadedProfile.trackedBazaarPositions.getFirst().remainingCost);
        assertEquals(1, loadedProfile.trackedAuctionPositions.size());
        assertEquals(800_000.0, loadedProfile.trackedAuctionPositions.getFirst().purchasePrice);
    }

    @Test
    void brokenStoreFallsBackToEmptyState(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        Files.writeString(storePath, "{broken");

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);

        assertTrue(loaded.profiles.isEmpty());
    }

    @Test
    void migratesOldMarketPriceTotalsToRealProfitTracking(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        Files.writeString(storePath, """
                {"profiles":{"orange":{"bazaarAllTimeProfit":123.0,"auctionHouseAllTimeProfit":456.0}}}
                """);

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);

        assertEquals(5, loaded.schemaVersion);
        assertEquals(0.0, loaded.getProfile("orange").bazaarAllTimeProfit);
        assertEquals(0.0, loaded.getProfile("orange").auctionHouseAllTimeProfit);
        assertTrue(loaded.getProfile("orange").trackedBazaarPositions.isEmpty());
        assertTrue(loaded.getProfile("orange").trackedAuctionPositions.isEmpty());
    }

    @Test
    void migratesOpenBazaarPurchasesToCashflow(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        Files.writeString(storePath, """
                {
                  "schemaVersion": 2,
                  "profiles": {
                    "orange": {
                      "bazaarAllTimeProfit": 1000.0,
                      "trackedBazaarPositions": [{
                        "itemId": "ENCHANTED_BREAD",
                        "itemName": "Enchanted Bread",
                        "remainingQuantity": 10,
                        "remainingCost": 600.0,
                        "purchasedAtMs": 1
                      }],
                      "pendingBazaarOrders": [{
                        "kind": "BUY_ORDER",
                        "itemId": "IRON_INGOT",
                        "itemName": "Iron Ingot",
                        "quantity": 20,
                        "quotedTotalCoins": 250.0,
                        "createdAtMs": 2
                      }]
                    }
                  }
                }
                """);

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);
        ProfileProfitState profile = loaded.getProfile("orange");

        assertEquals(5, loaded.schemaVersion);
        assertEquals(150.0, profile.bazaarAllTimeProfit);
        assertTrue(profile.pendingBazaarOrders.getFirst().purchaseCostRecorded);
    }

    @Test
    void upgradesVersionThreeStoresWithAnEmptyMinionProfit(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        Files.writeString(storePath, """
                {
                  "schemaVersion": 3,
                  "profiles": {
                    "orange": {
                      "bazaarAllTimeProfit": 123.0,
                      "auctionHouseAllTimeProfit": 456.0
                    }
                  }
                }
                """);

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);

        assertEquals(5, loaded.schemaVersion);
        assertEquals(0.0, loaded.getProfile("orange").minionAllTimeProfit);
        assertEquals(0.0, loaded.getProfile("orange").interestAllTimeProfit);
        assertEquals(0.0, loaded.getProfile("orange").allowanceAllTimeProfit);
        assertTrue(Files.readString(storePath).contains("\"schemaVersion\": 5"));
        assertTrue(Files.readString(storePath).contains("\"minionAllTimeProfit\": 0.0"));
        assertTrue(Files.readString(storePath).contains("\"interestAllTimeProfit\": 0.0"));
        assertTrue(Files.readString(storePath).contains("\"allowanceAllTimeProfit\": 0.0"));
    }
}
