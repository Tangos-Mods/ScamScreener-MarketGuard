package eu.tango.scamscreener.marketguard.auction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionReferencePriceTest {

    @Test
    void usesMedianSoOneManipulatedLowestBinDoesNotControlTheReference() {
        AuctionReferencePrice reference = AuctionReferencePrice.select(100_000.0, 1_000_000.0, 1_050_000.0).orElseThrow();

        assertEquals(1_000_000.0, reference.value());
        assertEquals(AuctionReferencePrice.Quality.LOW, reference.quality());
        assertFalse(reference.safeForProtection());
    }

    @Test
    void marksThreeConsistentSignalsAsHighQuality() {
        AuctionReferencePrice reference = AuctionReferencePrice.select(950_000.0, 1_000_000.0, 1_050_000.0).orElseThrow();

        assertEquals(1_000_000.0, reference.value());
        assertEquals(3, reference.signalCount());
        assertEquals(AuctionReferencePrice.Quality.HIGH, reference.quality());
        assertTrue(reference.safeForProtection());
    }

    @Test
    void averagesTwoConsistentSignalsWhenOneIsMissing() {
        AuctionReferencePrice reference = AuctionReferencePrice.select(900_000.0, 1_000_000.0, null).orElseThrow();

        assertEquals(950_000.0, reference.value());
        assertEquals(AuctionReferencePrice.Quality.MEDIUM, reference.quality());
        assertTrue(reference.safeForProtection());
    }

    @Test
    void lowestBinOnlyIsVisibleButNotSafeForBlockingProtection() {
        AuctionReferencePrice reference = AuctionReferencePrice.select(900_000.0, null, null).orElseThrow();

        assertEquals(900_000.0, reference.value());
        assertEquals(AuctionReferencePrice.Quality.LOW, reference.quality());
        assertFalse(reference.safeForProtection());
    }

    @Test
    void ignoresInvalidSignalsAndReturnsEmptyWithoutAUsablePrice() {
        assertTrue(AuctionReferencePrice.select(null, Double.NaN, -1.0).isEmpty());
    }
}
