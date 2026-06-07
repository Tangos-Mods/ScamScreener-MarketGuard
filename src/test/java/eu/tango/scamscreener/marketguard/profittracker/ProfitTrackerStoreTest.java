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
        profile.pendingBazaarOrders.add(new PendingBazaarOrder(
                BazaarTradeKind.BUY_ORDER,
                "ENCHANTED_BREAD",
                "Enchanted Bread",
                64,
                12_800.0,
                210.0,
                100L
        ));
        profile.pendingAuctionListings.add(new PendingAuctionListing(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                999_999.0,
                800_000.0,
                200L
        ));

        assertTrue(ProfitTrackerStore.save(storePath, state));

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);
        ProfileProfitState loadedProfile = loaded.getProfile("orange");

        assertNotNull(loadedProfile);
        assertEquals(123.45, loadedProfile.bazaarAllTimeProfit);
        assertEquals(678.9, loadedProfile.auctionHouseAllTimeProfit);
        assertEquals(1, loadedProfile.pendingBazaarOrders.size());
        assertEquals("ENCHANTED_BREAD", loadedProfile.pendingBazaarOrders.getFirst().itemId);
        assertEquals(1, loadedProfile.pendingAuctionListings.size());
        assertEquals("FANCY_LEGGINGS", loadedProfile.pendingAuctionListings.getFirst().itemId);
    }

    @Test
    void brokenStoreFallsBackToEmptyState(@TempDir Path tempDir) throws Exception {
        Path storePath = tempDir.resolve("profit_tracker.json");
        Files.writeString(storePath, "{broken");

        ProfitTrackerState loaded = ProfitTrackerStore.load(storePath);

        assertTrue(loaded.profiles.isEmpty());
    }
}
