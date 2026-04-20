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
}
