package eu.tango.scamscreener.marketguard.profittracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.tango.scamscreener.marketguard.MarketGuard;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ProfitTrackerStore {
    private static final int CURRENT_SCHEMA_VERSION = 5;
    private static final long TRACKED_ENTRY_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProfitTrackerStore() {}

    static ProfitTrackerState load() {
        return load(defaultPath());
    }

    static ProfitTrackerState load(Path path) {
        if (Files.notExists(path)) {
            return new ProfitTrackerState();
        }

        try {
            ProfitTrackerState state;
            try (Reader reader = Files.newBufferedReader(path)) {
                state = GSON.fromJson(reader, ProfitTrackerState.class);
            }
            if (state == null) {
                return new ProfitTrackerState();
            }
            if (state.profiles == null) {
                state.profiles = new java.util.LinkedHashMap<>();
            }
            int loadedSchemaVersion = state.schemaVersion;
            boolean needsProfitMigration = loadedSchemaVersion < 2;
            boolean needsBazaarCashflowMigration = loadedSchemaVersion == 2;
            boolean needsSchemaUpgrade = loadedSchemaVersion < CURRENT_SCHEMA_VERSION;
            state.schemaVersion = CURRENT_SCHEMA_VERSION;
            for (ProfileProfitState profile : state.profiles.values()) {
                if (profile.pendingBazaarOrders == null) {
                    profile.pendingBazaarOrders = new java.util.ArrayList<>();
                }
                if (profile.pendingAuctionListings == null) {
                    profile.pendingAuctionListings = new java.util.ArrayList<>();
                }
                if (profile.trackedBazaarPositions == null) {
                    profile.trackedBazaarPositions = new java.util.ArrayList<>();
                }
                if (profile.trackedAuctionPositions == null) {
                    profile.trackedAuctionPositions = new java.util.ArrayList<>();
                }
                if (needsProfitMigration) {
                    profile.bazaarAllTimeProfit = 0.0;
                    profile.auctionHouseAllTimeProfit = 0.0;
                } else if (needsBazaarCashflowMigration) {
                    for (TrackedBazaarPosition position : profile.trackedBazaarPositions) {
                        profile.bazaarAllTimeProfit -= position.remainingCost;
                    }
                    for (PendingBazaarOrder order : profile.pendingBazaarOrders) {
                        if (order.kind == BazaarTradeKind.BUY_ORDER) {
                            profile.bazaarAllTimeProfit -= order.quotedTotalCoins;
                            order.purchaseCostRecorded = true;
                        }
                    }
                }
            }
            dropExpiredEntries(state);
            if (needsProfitMigration || needsBazaarCashflowMigration || needsSchemaUpgrade) {
                save(path, state);
            }
            return state;
        } catch (Exception e) {
            MarketGuard.LOGGER.warn("Failed to load profit tracker state from {}", path, e);
            return new ProfitTrackerState();
        }
    }

    static boolean save(ProfitTrackerState state) {
        return save(defaultPath(), state);
    }

    static boolean save(Path path, ProfitTrackerState state) {
        try {
            state.schemaVersion = CURRENT_SCHEMA_VERSION;
            dropExpiredEntries(state);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            MarketGuard.LOGGER.warn("Failed to save profit tracker state to {}", path, e);
            return false;
        }
    }

    private static void dropExpiredEntries(ProfitTrackerState state) {
        long cutoff = System.currentTimeMillis() - TRACKED_ENTRY_MAX_AGE_MS;
        for (ProfileProfitState profile : state.profiles.values()) {
            profile.pendingAuctionListings.removeIf(listing -> listing.createdAtMs < cutoff);
            profile.trackedBazaarPositions.removeIf(position -> position.acquiredAtMs < cutoff);
            profile.trackedAuctionPositions.removeIf(position -> position.purchasedAtMs < cutoff);
        }
    }

    static Path defaultPath() {
        try {
            return FabricLoader.getInstance().getConfigDir()
                    .resolve("scamscreener_marketguard")
                    .resolve("profit_tracker.json");
        } catch (Exception ignored) {
            return Path.of("profit_tracker.json");
        }
    }
}
