package eu.tango.scamscreener.marketguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarketGuardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private static boolean debugEnabled = false;

    public static void load() {
        load(configPath());
    }

    public static boolean save() {
        return save(configPath());
    }

    static void load(Path path) {
        if (Files.notExists(path)) {
            save(path);
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                MarketGuard.LOGGER.warn("Config at {} was empty, rewriting defaults", path);
                save(path);
                return;
            }

            int underbiddingThreshold = root.has("underbiddingThreshold")
                    ? root.get("underbiddingThreshold").getAsInt()
                    : AuctionUnderbidding.getThreshold();
            int overbiddingThreshold = root.has("overbiddingThreshold")
                    ? root.get("overbiddingThreshold").getAsInt()
                    : AuctionOverbidding.getThreshold();
            debugEnabled = root.has("debug") && root.get("debug").getAsBoolean();

            AuctionUnderbidding.setThreshold(underbiddingThreshold);
            AuctionOverbidding.setThreshold(overbiddingThreshold);
        } catch (Exception e) {
            MarketGuard.LOGGER.warn("Failed to load config from {}, keeping current thresholds", path, e);
            save(path);
        }
    }

    static boolean save(Path path) {
        JsonObject root = new JsonObject();
        root.addProperty("underbiddingThreshold", AuctionUnderbidding.getThreshold());
        root.addProperty("overbiddingThreshold", AuctionOverbidding.getThreshold());
        root.addProperty("debug", debugEnabled);

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }

            return true;
        } catch (Exception e) {
            MarketGuard.LOGGER.warn("Failed to save config to {}", path, e);
            return false;
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("scamscreener_marketguard")
                .resolve("config.json");
    }
}
