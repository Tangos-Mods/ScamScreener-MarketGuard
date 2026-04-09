package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionPricingResolverTest {

    @Test
    void blockMissingLowestBinCancelsAndSchedulesBypass() {
        AuctionInteractEvent.Context context = mock(AuctionInteractEvent.Context.class);
        when(context.getRemainingBypassClicks()).thenReturn(3);

        AuctionPricingResolver.blockMissingLowestBin(context, null, "SNOW_RUNE;1");

        verify(context).cancel();
        verify(context).bypass(4);
    }
}
