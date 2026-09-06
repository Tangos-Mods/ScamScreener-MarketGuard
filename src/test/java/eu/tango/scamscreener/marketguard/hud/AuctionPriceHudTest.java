package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.tangosHudLib.api.HudContent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AuctionPriceHudTest {

    @Test
    void warnsWhenAuctionIsSlightlyAboveReliableReference() {
        HudContent content = AuctionPriceHud.content(view(1_060_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertTrue(lines(content).contains("Reference: 1,000,000 coins (high quality, 3 signals)"));
        assertTrue(lines(content).contains("Caution: 6.0% above reference."));
        assertTrue(lines(content).contains("Difference to reference: +60,000 coins (+6.0%)"));
    }

    @Test
    void highlightsExtremelyCheapAuction() {
        HudContent content = AuctionPriceHud.content(view(750_000.0, 1_000_000.0, 900_000.0, 1_000_000.0));

        assertTrue(lines(content).contains("Great deal: 25.0% below reference."));
        assertTrue(lines(content).contains("Difference to reference: -250,000 coins (-25.0%)"));
    }

    @Test
    void showsLoadingStateUntilLowestBinIsAvailable() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View("FANCY_LEGGINGS", "Fancy Leggings", 1_000_000.0, null));

        assertTrue(lines(content).contains("Loading reference price..."));
    }

    @Test
    void explainsWhenNoReferenceDataExistsForTheItem() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                1_000_000.0,
                new LowestBinData.LookupResult(null, null, false, false, false)
        ));

        assertTrue(lines(content).contains("No reference price data for this item."));
    }

    @Test
    void labelsLowestBinOnlyAsLowQualityInsteadOfGivingPriceAdvice() {
        HudContent content = AuctionPriceHud.content(view(1_060_000.0, 1_000_000.0, null, null));

        assertTrue(lines(content).contains("Reference: 1,000,000 coins (low quality, 1 signal)"));
        assertTrue(lines(content).contains("Price advice limited: low data quality."));
        assertFalse(lines(content).stream().anyMatch(line -> line.startsWith("Caution:")));
    }

    @Test
    void manipulatedLowestBinDoesNotControlDisplayedReference() {
        HudContent content = AuctionPriceHud.content(view(1_000_000.0, 100_000.0, 1_000_000.0, 1_050_000.0));

        assertTrue(lines(content).contains("Reference: 1,000,000 coins (low quality, 3 signals)"));
        assertTrue(lines(content).contains("Difference to reference: 0 coins (0.0%)"));
    }

    @Test
    void warnsWhenShortAndLongTermAuctionAveragesDiverge() {
        HudContent content = AuctionPriceHud.content(view(1_100_000.0, 1_100_000.0, 1_200_000.0, 1_000_000.0));

        assertTrue(lines(content).contains("Price volatility: 7d avg is 20.0% above 30d avg."));
    }

    @Test
    void warnsWhenBazaarMarketForTheItemIsIlliquid() {
        BazaarData.Product product = new BazaarData.Product(
                "Fancy Leggings",
                100.0,
                80.0,
                400L,
                800L,
                3_000L,
                8_000L
        );
        HudContent content;
        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(product, false, false, false));
            content = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
        }

        assertTrue(lines(content).contains(
                "Bazaar liquidity risk: 20.0% instant spread, 400 units on thinner book side, 3,000/week on slower side."
        ));
    }

    @Test
    void staysHiddenOutsideAnIndividualAuctionView() {
        HudContent content = AuctionPriceHud.content(AuctionPriceHud.View.hidden());

        assertFalse(content.visible());
        assertEquals(List.of("Auction Price"), lines(content));
    }

    @Test
    void hidesWhenTheBinAuctionViewCloses() {
        AuctionPriceHud.update("ROCK", "Rock", 4_242_911_000D);

        AuctionPriceHud.clear();

        assertFalse(AuctionPriceHud.Widgets.auctionPrice().visible());
    }

    private static AuctionPriceHud.View view(double auctionPrice, double lowestBin, Double average7d, Double average30d) {
        return new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                auctionPrice,
                new LowestBinData.LookupResult(lowestBin, average7d, average30d, false, false, false)
        );
    }

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(line -> line.getString()).toList();
    }
}
