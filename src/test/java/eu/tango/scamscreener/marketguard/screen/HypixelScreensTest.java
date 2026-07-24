package eu.tango.scamscreener.marketguard.screen;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HypixelScreensTest {

    @Test
    void resolvesTradePartnerFromHypixelTradeTitle() {
        assertEquals("Jbkid", HypixelScreens.tradePartner("You                  Jbkid"));
        assertEquals("Jbkid", HypixelScreens.tradePartner("You Jbkid"));
    }

    @Test
    void restoresLongTradeNamesFromTheRecentTradeMessage() {
        HypixelScreens.rememberTradePartner("You have sent a trade request to SixTheRaccoon.");
        assertEquals("SixTheRaccoon", HypixelScreens.tradePartner("You                  SixTheRacc"));
    }

    @Test
    void ignoresNonTradeAndInvalidTradeTitles() {
        assertNull(HypixelScreens.tradePartner("Trade"));
        assertNull(HypixelScreens.tradePartner("You"));
        assertNull(HypixelScreens.tradePartner("You Jb"));
        assertNull(HypixelScreens.tradePartner("You Jbkid Extra"));
    }

    @Test
    void readsSellerFromBinAuctionLore() {
        assertEquals("Pankraz01", HypixelScreens.auctionSeller(List.of(
                Component.literal(""),
                Component.literal("Seller: Pankraz01"),
                Component.literal("Buy it now: 12,000 coins")
        )));
    }

    @Test
    void ignoresNonPlayerSellerValues() {
        assertNull(HypixelScreens.auctionSeller(List.of(Component.literal("Seller: Auction House"))));
    }
}
