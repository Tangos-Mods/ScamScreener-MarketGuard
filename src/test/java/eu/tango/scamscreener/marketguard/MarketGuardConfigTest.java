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
        MarketGuardConfig.setAbsoluteThreshold(MarketGuardConfig.DEFAULT_ABSOLUTE_THRESHOLD);
        MarketGuardConfig.setDebugEnabled(false);
        MarketGuardConfig.setWarnOnUnmatchedProfitConfirmations(false);
    }

    @Test
    void savesAndLoadsThresholds(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");

        AuctionUnderbidding.setThreshold(70);
        AuctionOverbidding.setThreshold(140);
        MarketGuardConfig.setAbsoluteThreshold(25_000L);
        MarketGuardConfig.setWarnOnUnmatchedProfitConfirmations(true);
        assertTrue(MarketGuardConfig.save(configPath));

        String json = Files.readString(configPath);
        assertTrue(json.contains("\"underbiddingThreshold\": 70"));
        assertTrue(json.contains("\"overbiddingThreshold\": 140"));
        assertTrue(json.contains("\"absoluteThreshold\": 25000"));
        assertTrue(json.contains("\"debug\": false"));
        assertTrue(json.contains("\"warnOnUnmatchedProfitConfirmations\": true"));

        AuctionUnderbidding.setThreshold(80);
        AuctionOverbidding.setThreshold(120);
        MarketGuardConfig.setAbsoluteThreshold(MarketGuardConfig.DEFAULT_ABSOLUTE_THRESHOLD);
        MarketGuardConfig.setDebugEnabled(true);
        MarketGuardConfig.setWarnOnUnmatchedProfitConfirmations(false);

        MarketGuardConfig.load(configPath);

        assertEquals(70, AuctionUnderbidding.getThreshold());
        assertEquals(140, AuctionOverbidding.getThreshold());
        assertEquals(25_000L, MarketGuardConfig.getAbsoluteThreshold());
        assertEquals(false, MarketGuardConfig.isDebugEnabled());
        assertEquals(true, MarketGuardConfig.isWarnOnUnmatchedProfitConfirmations());
    }

    @Test
    void missingConfigWritesDefaults(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");

        MarketGuardConfig.load(configPath);

        assertTrue(Files.exists(configPath));
        assertTrue(Files.readString(configPath).contains("\"underbiddingThreshold\": 80"));
        assertTrue(Files.readString(configPath).contains("\"overbiddingThreshold\": 120"));
        assertTrue(Files.readString(configPath).contains("\"absoluteThreshold\": 10000"));
        assertTrue(Files.readString(configPath).contains("\"debug\": false"));
        assertTrue(Files.readString(configPath).contains("\"warnOnUnmatchedProfitConfirmations\": false"));
    }

    @Test
    void loadsDebugFlag(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "underbiddingThreshold": 80,
                  "overbiddingThreshold": 120,
                  "absoluteThreshold": 10000,
                  "debug": true,
                  "warnOnUnmatchedProfitConfirmations": false
                }
                """);

        MarketGuardConfig.load(configPath);

        assertTrue(MarketGuardConfig.isDebugEnabled());
    }

    @Test
    void loadsAbsoluteThreshold(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("marketguard").resolve("config.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "underbiddingThreshold": 80,
                  "overbiddingThreshold": 120,
                  "absoluteThreshold": 42000,
                  "debug": false,
                  "warnOnUnmatchedProfitConfirmations": true
                }
                """);

        MarketGuardConfig.load(configPath);

        assertEquals(42_000L, MarketGuardConfig.getAbsoluteThreshold());
        assertTrue(MarketGuardConfig.isWarnOnUnmatchedProfitConfirmations());
    }
}
