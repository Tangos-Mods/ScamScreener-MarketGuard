package eu.tango.scamscreener.marketguard.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageBuilderTest {

    @Test
    void resolveDisplayItemNamePrefersActualStackName() {
        assertEquals(
                "Music Disc - 13",
                MessageBuilder.resolveDisplayItemName("Music Disc - 13", "RECORD_1")
        );
    }

    @Test
    void resolveDisplayItemNameFallsBackToFormattedItemId() {
        assertEquals(
                "Fancy Leggings",
                MessageBuilder.resolveDisplayItemName(null, "FANCY_LEGGINGS;5")
        );
    }

    @Test
    void buildUnderbiddingMessageIncludesLowestPossiblePrice() {
        assertEquals(
                "[MarketGuard] You are underbidding Fancy Leggings by 12.34%. Lowest possible price is 1,234 (4 clicks until bypass)",
                MessageBuilder.buildUnderbiddingMessage("FANCY_LEGGINGS;5", "Fancy Leggings", 12.34, 1234.0, 4).getString()
        );
    }

    @Test
    void buildOverbiddingMessageIncludesHighestPossiblePrice() {
        assertEquals(
                "[MarketGuard] You are overbidding Fancy Leggings by 12.34%. Highest possible price is 1,234.50 (4 clicks until bypass)",
                MessageBuilder.buildOverbiddingMessage("FANCY_LEGGINGS;5", "Fancy Leggings", 12.34, 1234.5, 4).getString()
        );
    }
}
