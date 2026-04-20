package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AuctionOverbiddingTest {

    @AfterEach
    void resetThreshold() {
        AuctionOverbidding.setThreshold(120);
    }

    @Test
    void onInteractTriggersBlacklistCheckForBinBuyClick() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.isBinView()).thenReturn(true);
        when(context.getAuctionItemId()).thenReturn("FANCY_LEGGINGS");
        when(context.getMc()).thenReturn(null);

        try (MockedStatic<LowestBIN> lowestBin = mockStatic(LowestBIN.class)) {
            AuctionOverbidding.onInteract(context);

            lowestBin.verify(() -> LowestBIN.checkBlacklistedAuctioneerAsyncIfNeeded("FANCY_LEGGINGS"));
        }
    }

    @Test
    void onInteractSkipsBlacklistCheckWhenClickWasNotBinBuyClick() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.isBinView()).thenReturn(false);

        try (MockedStatic<LowestBIN> lowestBin = mockStatic(LowestBIN.class)) {
            AuctionOverbidding.onInteract(context);

            lowestBin.verifyNoInteractions();
        }
    }
}
