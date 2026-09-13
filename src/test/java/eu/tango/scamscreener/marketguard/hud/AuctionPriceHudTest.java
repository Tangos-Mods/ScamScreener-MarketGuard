package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.tangosHudLib.api.HudContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class AuctionPriceHudTest {

    @AfterEach
    void resetDefaults() {
        MarketGuardConfig.setUnderbiddingThreshold(AuctionUnderbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.setOverbiddingThreshold(AuctionOverbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(List.of("item", "auction", "lowest_bin", "advice", "!difference", "volatility", "liquidity", "stale"));
    }

    @Test
    void showsMarketPriceAndCallsAPriceNearMarketFair() {
        HudContent content = AuctionPriceHud.content(view(1_020_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(List.of("Fancy Leggings", "This auction: 1,020,000 coins", "Market price: ~1,000,000 coins", "Fair price"), lines(content));
        assertEquals(color(ChatFormatting.GRAY), color(line(content, "Market price: ~1,000,000 coins")));
        assertEquals(color(ChatFormatting.GREEN), color(line(content, "Fair price")));
    }

    @Test
    void callsAnAuctionBelowTheUnderbiddingThresholdAGoodDeal() {
        HudContent content = AuctionPriceHud.content(view(750_000.0, 1_000_000.0, 900_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.GREEN), color(line(content, "Good deal: 25% below market")));
    }

    @Test
    void warnsWhenAuctionIsSlightlyAboveMarket() {
        HudContent content = AuctionPriceHud.content(view(1_120_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.YELLOW), color(line(content, "12% above market")));
    }

    @Test
    void flagsAnAuctionAtTheOverbiddingThresholdAsOverpriced() {
        HudContent content = AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.RED), color(line(content, "25% above market - overpriced")));
    }

    @Test
    void verdictFollowsTheConfiguredProtectionThresholds() {
        MarketGuardConfig.setOverbiddingThreshold(150);
        MarketGuardConfig.setUnderbiddingThreshold(60);

        assertTrue(lines(AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("25% above market"));
        assertTrue(lines(AuctionPriceHud.content(view(1_500_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("50% above market - overpriced"));
        assertTrue(lines(AuctionPriceHud.content(view(750_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Fair price"));
        assertTrue(lines(AuctionPriceHud.content(view(600_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Good deal: 40% below market"));
    }

    @Test
    void verdictFallsBackToTheDefaultBandsWhenAProtectionIsDisabled() {
        MarketGuardConfig.setOverbiddingThreshold(100);
        MarketGuardConfig.setUnderbiddingThreshold(100);

        assertEquals(color(ChatFormatting.YELLOW),
                color(line(AuctionPriceHud.content(view(1_120_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0)), "12% above market")));
        assertTrue(lines(AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Fair price"));
        assertTrue(lines(AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("25% above market - overpriced"));

        MarketGuardConfig.setUnderbiddingThreshold(0);

        assertTrue(lines(AuctionPriceHud.content(view(400_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Good deal: 60% below market"));
        assertTrue(lines(AuctionPriceHud.content(view(900_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0))).contains("Fair price"));
    }

    @Test
    void softensTheVerdictWhenTheMarketPriceIsBasedOnFewSales() {
        HudContent above = AuctionPriceHud.content(view(1_250_000.0, 1_000_000.0, null, null));
        HudContent below = AuctionPriceHud.content(view(750_000.0, 1_000_000.0, null, null));
        HudContent near = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, null, null));

        assertEquals(color(ChatFormatting.YELLOW), color(line(above, "Market price: ~1,000,000 coins (few recent sales)")));
        assertEquals(color(ChatFormatting.YELLOW), color(line(above, "Roughly 25% above market")));
        assertEquals(color(ChatFormatting.YELLOW), color(line(below, "Roughly 25% below market")));
        assertTrue(lines(near).contains("Fair price"));
    }

    @Test
    void showsTheDifferenceRowOnlyWhenEnabled() {
        HudContent hidden = AuctionPriceHud.content(view(1_060_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
        assertFalse(lines(hidden).stream().anyMatch(line -> line.endsWith("vs. market")));

        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(List.of("item", "auction", "lowest_bin", "advice", "difference"));
        HudContent shown = AuctionPriceHud.content(view(1_060_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.YELLOW), color(line(shown, "+60,000 coins (+6%) vs. market")));
    }

    @Test
    void showsLoadingStateUntilLowestBinIsAvailable() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View("FANCY_LEGGINGS", "Fancy Leggings", 1_000_000.0, null));

        assertTrue(lines(content).contains("Loading market price..."));
    }

    @Test
    void explainsWhenNoMarketPriceExistsForTheItem() {
        HudContent missing = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                1_000_000.0,
                new LowestBinData.LookupResult(null, null, null, false, false, false)
        ));
        HudContent failed = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                1_000_000.0,
                new LowestBinData.LookupResult(null, null, null, false, false, true)
        ));

        assertTrue(lines(missing).contains("No market price for this item"));
        assertTrue(lines(failed).contains("Market price unavailable"));
    }

    @Test
    void manipulatedLowestBinDoesNotControlDisplayedMarketPrice() {
        HudContent content = AuctionPriceHud.content(view(1_000_000.0, 100_000.0, 1_000_000.0, 1_050_000.0));

        assertTrue(lines(content).contains("Market price: ~1,000,000 coins (few recent sales)"));
        assertTrue(lines(content).contains("Fair price"));
    }

    @Test
    void showsPriceTrendOnlyWhenAveragesDivergeStrongly() {
        HudContent falling = AuctionPriceHud.content(view(650_000.0, 650_000.0, 650_000.0, 1_000_000.0));
        HudContent mild = AuctionPriceHud.content(view(1_100_000.0, 1_100_000.0, 1_200_000.0, 1_000_000.0));

        assertEquals(color(ChatFormatting.YELLOW), color(line(falling, "Price trend: falling 35% vs. last month")));
        assertFalse(lines(mild).stream().anyMatch(line -> line.startsWith("Price trend")));
    }

    @Test
    void warnsOnlyWhenTheBazaarMarketForTheItemIsHardToResell() {
        BazaarData.Product illiquid = new BazaarData.Product("Fancy Leggings", 100.0, 80.0, 400L, 800L, 3_000L, 8_000L);
        BazaarData.Product liquid = new BazaarData.Product("Fancy Leggings", 100.0, 92.0, 50_000L, 60_000L, 500_000L, 600_000L);
        HudContent fresh;
        HudContent stale;
        HudContent fine;
        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(illiquid, false, false, false));
            fresh = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(illiquid, true, false, false));
            stale = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
            bazaar.when(() -> BazaarData.lookupProduct("FANCY_LEGGINGS"))
                    .thenReturn(new BazaarData.LookupResult(liquid, false, false, false));
            fine = AuctionPriceHud.content(view(1_000_000.0, 1_000_000.0, 1_000_000.0, 1_000_000.0));
        }

        assertEquals(color(ChatFormatting.YELLOW), color(line(fresh, "Hard to resell on the Bazaar")));
        assertTrue(lines(stale).contains("Hard to resell on the Bazaar (Bazaar data may be outdated)"));
        assertFalse(lines(fine).stream().anyMatch(line -> line.startsWith("Hard to resell")));
    }

    @Test
    void warnsWhenAuctionPricesMayBeOutdated() {
        HudContent content = AuctionPriceHud.content(new AuctionPriceHud.View(
                "FANCY_LEGGINGS",
                "Fancy Leggings",
                1_000_000.0,
                new LowestBinData.LookupResult(1_000_000.0, 1_000_000.0, 1_000_000.0, true, false, false)
        ));

        assertEquals(color(ChatFormatting.YELLOW), color(line(content, "Prices may be outdated")));
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
        return content.lines().stream().map(Component::getString).toList();
    }

    private static Component line(HudContent content, String text) {
        return content.lines().stream()
                .filter(line -> line.getString().equals(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing line: " + text + " in " + lines(content)));
    }

    private static int color(Component line) {
        return line.getStyle().getColor().getValue();
    }

    private static int color(ChatFormatting formatting) {
        return TextColor.fromLegacyFormat(formatting).getValue();
    }
}
