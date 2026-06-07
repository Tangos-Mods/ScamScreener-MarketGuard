package eu.tango.scamscreener.marketguard.auction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuctionProtectionThresholdTest {

    @AfterEach
    void resetDefaults() {
        AuctionUnderbidding.setThreshold(80);
        AuctionOverbidding.setThreshold(120);
        eu.tango.scamscreener.marketguard.MarketGuardConfig.setAbsoluteThreshold(
                eu.tango.scamscreener.marketguard.MarketGuardConfig.DEFAULT_ABSOLUTE_THRESHOLD
        );
    }

    @Test
    void underbiddingThresholdUsesLowestBinPercentage() {
        AuctionUnderbidding.setThreshold(99);

        assertEquals(99, AuctionUnderbidding.getThreshold());
        assertEquals(0.99, AuctionUnderbidding.getMinimumAllowedPercentage());
        assertTrue(AuctionUnderbidding.isEnabled());

        AuctionUnderbidding.setThreshold(0);

        assertFalse(AuctionUnderbidding.isEnabled());

        AuctionUnderbidding.setThreshold(100);

        assertFalse(AuctionUnderbidding.isEnabled());
    }

    @Test
    void overbiddingThresholdUsesSymmetricDeviation() {
        AuctionOverbidding.setThreshold(150);

        assertEquals(150, AuctionOverbidding.getThreshold());
        assertEquals(1.5, AuctionOverbidding.getMaximumAllowedPercentage());
        assertTrue(AuctionOverbidding.isEnabled());

        AuctionOverbidding.setThreshold(100);

        assertFalse(AuctionOverbidding.isEnabled());
    }

    @Test
    void thresholdsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> AuctionUnderbidding.setThreshold(101));
        assertThrows(IllegalArgumentException.class, () -> AuctionOverbidding.setThreshold(99));
        assertThrows(IllegalArgumentException.class, () -> eu.tango.scamscreener.marketguard.MarketGuardConfig.setAbsoluteThreshold(-1));
    }

    @Test
    void absoluteThresholdMustAlsoBeExceeded() {
        eu.tango.scamscreener.marketguard.MarketGuardConfig.setAbsoluteThreshold(10_000L);

        assertFalse(AuctionUnderbidding.exceedsAbsoluteThreshold(9_999.99));
        assertTrue(AuctionUnderbidding.exceedsAbsoluteThreshold(10_000.0));

        assertFalse(AuctionOverbidding.exceedsAbsoluteThreshold(9_999.99));
        assertTrue(AuctionOverbidding.exceedsAbsoluteThreshold(10_000.0));
    }

    @Test
    void triggerCancelsAndSchedulesBypass() {
        eu.tango.scamscreener.marketguard.events.AuctionInteractEvent.Context context =
                mock(eu.tango.scamscreener.marketguard.events.AuctionInteractEvent.Context.class);

        AuctionProtectionChecks.trigger(context, "Overbidding", "overbidPercent", "FANCY_LEGGINGS", 15.0);

        verify(context).cancel();
        verify(context).bypass(4);
    }
}
