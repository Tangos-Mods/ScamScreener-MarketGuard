package eu.tango.scamscreener.marketguard.profittracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitTrackerStoreTest {

    @Test
    void savesAndLoadsProfilesWithPendingEntries(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        long now = System.currentTimeMillis();
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
                now - 100L
        ));
        profile.pendingAuctionListings.add(new PendingAuctionListing(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                999_999.0,
                now - 200L
        ));
        profile.trackedBazaarPositions.add(new TrackedBazaarPosition(
                "ENCHANTED_BREAD",
                "Enchanted Bread",
                32,
                6_400.0,
                now - 300L
        ));
        profile.trackedAuctionPositions.add(new TrackedAuctionPosition(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                800_000.0,
                now - 400L
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
    void saveReplacesTheStoreWithoutLeavingATempFile(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        ProfitTrackerState state = new ProfitTrackerState();
        state.getOrCreateProfile("orange").bazaarAllTimeProfit = 1.0;
        assertTrue(ProfitTrackerStore.save(storePath, state));

        state.getProfile("orange").bazaarAllTimeProfit = 2.0;
        assertTrue(ProfitTrackerStore.save(storePath, state));

        assertFalse(Files.exists(tempDir.resolve("profit_tracker.json.tmp")));
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(storePath), files.toList());
        }
        assertEquals(2.0, ProfitTrackerStore.load(storePath).getProfile("orange").bazaarAllTimeProfit);
    }

    @Test
    void failedSaveKeepsThePreviousStore(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        ProfitTrackerState state = new ProfitTrackerState();
        ProfileProfitState profile = state.getOrCreateProfile("orange");
        profile.bazaarAllTimeProfit = 1.0;
        assertTrue(ProfitTrackerStore.save(storePath, state));

        profile.bazaarAllTimeProfit = Double.NaN;
        assertFalse(ProfitTrackerStore.save(storePath, state));

        assertEquals(1.0, ProfitTrackerStore.load(storePath).getProfile("orange").bazaarAllTimeProfit);
    }

    @Test
    void dropsAuctionEntriesOlderThanFourteenDaysOnLoad(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        long now = System.currentTimeMillis();
        long fifteenDaysAgo = now - 15L * 24 * 60 * 60 * 1000;
        Files.writeString(storePath, """
                {
                  "schemaVersion": 5,
                  "profiles": {
                    "orange": {
                      "pendingAuctionListings": [
                        {"itemId": "OLD_LISTING", "itemName": "Old Listing", "salePrice": 1.0, "createdAtMs": %d},
                        {"itemId": "NEW_LISTING", "itemName": "New Listing", "salePrice": 2.0, "createdAtMs": %d}
                      ],
                      "trackedBazaarPositions": [
                        {"itemId": "OLD_LOT", "itemName": "Old Lot", "remainingQuantity": 1, "remainingCost": 1.0, "acquiredAtMs": %d}
                      ],
                      "trackedAuctionPositions": [
                        {"itemId": "OLD_POSITION", "itemName": "Old Position", "purchasePrice": 1.0, "purchasedAtMs": %d},
                        {"itemId": "NEW_POSITION", "itemName": "New Position", "purchasePrice": 2.0, "purchasedAtMs": %d}
                      ]
                    }
                  }
                }
                """.formatted(fifteenDaysAgo, now, fifteenDaysAgo, fifteenDaysAgo, now));

        ProfileProfitState profile = ProfitTrackerStore.load(storePath).getProfile("orange");

        assertEquals(1, profile.pendingAuctionListings.size());
        assertEquals("NEW_LISTING", profile.pendingAuctionListings.getFirst().itemId);
        assertTrue(profile.trackedBazaarPositions.isEmpty());
        assertEquals(1, profile.trackedAuctionPositions.size());
        assertEquals("NEW_POSITION", profile.trackedAuctionPositions.getFirst().itemId);
    }

    @Test
    void dropsAuctionEntriesOlderThanFourteenDaysOnSave(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        long now = System.currentTimeMillis();
        long fifteenDaysAgo = now - 15L * 24 * 60 * 60 * 1000;
        ProfitTrackerState state = new ProfitTrackerState();
        ProfileProfitState profile = state.getOrCreateProfile("orange");
        profile.pendingAuctionListings.add(new PendingAuctionListing("OLD_LISTING", "Old Listing", 1.0, fifteenDaysAgo));
        profile.pendingAuctionListings.add(new PendingAuctionListing("NEW_LISTING", "New Listing", 2.0, now));
        profile.trackedAuctionPositions.add(new TrackedAuctionPosition("OLD_POSITION", "Old Position", 1.0, fifteenDaysAgo));
        profile.trackedAuctionPositions.add(new TrackedAuctionPosition("NEW_POSITION", "New Position", 2.0, now));

        assertTrue(ProfitTrackerStore.save(storePath, state));

        String json = Files.readString(storePath);
        assertFalse(json.contains("OLD_LISTING"));
        assertTrue(json.contains("NEW_LISTING"));
        assertFalse(json.contains("OLD_POSITION"));
        assertTrue(json.contains("NEW_POSITION"));
        assertEquals(1, profile.pendingAuctionListings.size());
        assertEquals(1, profile.trackedAuctionPositions.size());
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
