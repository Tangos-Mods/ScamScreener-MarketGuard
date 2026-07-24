package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.tangosHudLib.api.HudContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionPriceHudTest {

    @Test
    void warnsWhenAuctionIsSlightlyAboveLowestBin() {
        HudContent content = AuctionPriceHud.content(view(1_060_000.0, 1_000_000.0, 800_000.0));

        assertTrue(lines(content).contains("Caution: 6.0% above Lowest BIN."));
        assertTrue(lines(content).contains("Difference to 7d avg: +260,000 coins (+32.5%)"));
    }

    @Test
    void highlightsExtremelyCheapAuction() {
        HudContent content = AuctionPriceHud.content(view(750_000.0, 1_000_000.0, 900_000.0));

        assertTrue(lines(content).contains("Great deal: 25.0% below Lowest BIN."));
        assertTrue(lines(content).contains("Difference to 7d avg: -150,000 coins (-16.7%)"));
    }

    @Test
    void showsLoadingStateUntilLowestBinIsAvailable() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View("FANCY_LEGGINGS", "Fancy Leggings", 1_000_000.0, null));

        assertTrue(lines(content).contains("Loading Lowest BIN..."));
    }

    @Test
    void explainsWhenNoLowestBinExistsForTheItem() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                1_000_000.0,
                new LowestBinData.LookupResult(null, null, false, false, false)
        ));

        assertTrue(lines(content).contains("No Lowest BIN data for this item."));
    }

    @Test
    void doesNotFallBackToLowestBinWhenSevenDayAverageIsMissing() {
        HudContent content = AuctionPriceHud.content(view(1_060_000.0, 1_000_000.0, null));

        assertTrue(lines(content).contains("7d average unavailable."));
        assertFalse(lines(content).stream().anyMatch(line -> line.startsWith("Difference to 7d avg:")));
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

    private static AuctionPriceHud.View view(double auctionPrice, double lowestBin, Double average7d) {
        return new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                auctionPrice,
                new LowestBinData.LookupResult(lowestBin, average7d, false, false, false)
        );
    }

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(line -> line.getString()).toList();
    }
}
