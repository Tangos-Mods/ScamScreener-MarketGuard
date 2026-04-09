package eu.tango.scamscreener.marketguard;

import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketGuardConfigTest {

    @AfterEach
    void resetDefaults() {
        AuctionUnderbidding.setThreshold(80);
        AuctionOverbidding.setThreshold(120);
        MarketGuardConfig.setDebugEnabled(false);
    }

    @Test
    void savesAndLoadsThresholds(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");

        AuctionUnderbidding.setThreshold(70);
        AuctionOverbidding.setThreshold(140);
        assertTrue(MarketGuardConfig.save(configPath));

        String json = Files.readString(configPath);
        assertTrue(json.contains("\"underbiddingThreshold\": 70"));
        assertTrue(json.contains("\"overbiddingThreshold\": 140"));
        assertTrue(json.contains("\"debug\": false"));

        AuctionUnderbidding.setThreshold(80);
        AuctionOverbidding.setThreshold(120);
        MarketGuardConfig.setDebugEnabled(true);

        MarketGuardConfig.load(configPath);

        assertEquals(70, AuctionUnderbidding.getThreshold());
        assertEquals(140, AuctionOverbidding.getThreshold());
        assertEquals(false, MarketGuardConfig.isDebugEnabled());
    }

    @Test
    void missingConfigWritesDefaults(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");

        MarketGuardConfig.load(configPath);

        assertTrue(Files.exists(configPath));
        assertTrue(Files.readString(configPath).contains("\"underbiddingThreshold\": 80"));
        assertTrue(Files.readString(configPath).contains("\"overbiddingThreshold\": 120"));
        assertTrue(Files.readString(configPath).contains("\"debug\": false"));
    }

    @Test
    void loadsDebugFlag(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "underbiddingThreshold": 80,
                  "overbiddingThreshold": 120,
                  "debug": true
                }
                """);

        MarketGuardConfig.load(configPath);

        assertTrue(MarketGuardConfig.isDebugEnabled());
    }
}
