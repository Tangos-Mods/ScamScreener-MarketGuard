package eu.tango.scamscreener.marketguard.auction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionProtectionThresholdTest {

    @AfterEach
    void resetDefaults() {
        AuctionUnderbidding.setThreshold(80);
        AuctionOverbidding.setThreshold(120);
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
    }
}
