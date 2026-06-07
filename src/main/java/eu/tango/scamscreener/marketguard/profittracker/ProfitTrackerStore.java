package eu.tango.scamscreener.marketguard.profittracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.tango.scamscreener.marketguard.MarketGuard;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class ProfitTrackerStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProfitTrackerStore() {}

    static ProfitTrackerState load() {
        return load(defaultPath());
    }

    static ProfitTrackerState load(Path path) {
        if (Files.notExists(path)) {
            return new ProfitTrackerState();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            ProfitTrackerState state = GSON.fromJson(reader, ProfitTrackerState.class);
            if (state == null) {
                return new ProfitTrackerState();
            }
            if (state.profiles == null) {
                state.profiles = new java.util.LinkedHashMap<>();
            }
            for (ProfileProfitState profile : state.profiles.values()) {
                if (profile.pendingBazaarOrders == null) {
                    profile.pendingBazaarOrders = new java.util.ArrayList<>();
                }
                if (profile.pendingAuctionListings == null) {
                    profile.pendingAuctionListings = new java.util.ArrayList<>();
                }
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
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(state, writer);
            }
            return true;
        } catch (Exception e) {
            MarketGuard.LOGGER.warn("Failed to save profit tracker state to {}", path, e);
            return false;
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
