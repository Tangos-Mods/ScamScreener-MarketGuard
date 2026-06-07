package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionUnderbiddingTest {

    @Test
    void onInteractReturnsImmediatelyWhenNotCreateBinClick() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.isCreateBinClick()).thenReturn(false);

        AuctionUnderbidding.onInteract(context);

        verify(context, never()).cancel();
        verify(context, never()).getAuctionItemStack();
    }
}
