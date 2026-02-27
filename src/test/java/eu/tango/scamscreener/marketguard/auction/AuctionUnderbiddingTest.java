package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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

    @Test
    void fetchLowestBinReturnsLowestBinValue() throws Exception {
        try (MockedStatic<LowestBIN> lowestBin = mockStatic(LowestBIN.class)) {
            lowestBin.when(() -> LowestBIN.getLowestBIN("FANCY_LEGGINGS")).thenReturn(100.0);

            Method method = AuctionUnderbidding.class.getDeclaredMethod("fetchLowestBin", String.class);
            method.setAccessible(true);
            Double result = (Double) method.invoke(null, "FANCY_LEGGINGS");

            assertEquals(100.0, result);
        }
    }

    @Test
    void fetchLowestBinReturnsNullWhenLookupFails() throws Exception {
        try (MockedStatic<LowestBIN> lowestBin = mockStatic(LowestBIN.class)) {
            lowestBin.when(() -> LowestBIN.getLowestBIN("MISSING_ITEM"))
                    .thenThrow(new RuntimeException("boom"));

            Method method = AuctionUnderbidding.class.getDeclaredMethod("fetchLowestBin", String.class);
            method.setAccessible(true);
            Double result = (Double) method.invoke(null, "MISSING_ITEM");

            assertNull(result);
        }
    }
}
