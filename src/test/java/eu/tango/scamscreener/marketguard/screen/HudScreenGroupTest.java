package eu.tango.scamscreener.marketguard.screen;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudScreenGroupTest {
    @Test
    void classifiesTheKnownScreenGroups() {
        assertEquals(Set.of(HudScreenGroup.INGAME), HudScreenGroup.classify(null));
        assertTrue(HudScreenGroup.classify("Auction House").contains(HudScreenGroup.AUCTION_HOUSE));
        assertTrue(HudScreenGroup.classify("Bin Auction View").contains(HudScreenGroup.BIN_VIEW));
        assertTrue(HudScreenGroup.classify("You Pankraz01").contains(HudScreenGroup.TRADE));
        assertTrue(HudScreenGroup.classify("Pankraz01's Profile").contains(HudScreenGroup.PROFILE));
        assertTrue(HudScreenGroup.classify("Gold Minion X").contains(HudScreenGroup.MINION));
        assertTrue(HudScreenGroup.classify("The Forge").contains(HudScreenGroup.FORGE));
    }

    @Test
    void configKeysDoNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.of("tr"));
        try {
            assertEquals("ingame", HudScreenGroup.INGAME.key());
            assertEquals("bin_view", HudScreenGroup.BIN_VIEW.key());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
