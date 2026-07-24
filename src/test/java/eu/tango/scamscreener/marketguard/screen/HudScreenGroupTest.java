package eu.tango.scamscreener.marketguard.screen;

import org.junit.jupiter.api.Test;

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
}
