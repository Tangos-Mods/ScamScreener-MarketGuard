package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.BazaarProfit;
import eu.tango.tangosHudLib.api.HudContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeProfitHudTest {

    @Test
    void matchesOnlyTheForgeScreen() {
        assertTrue(ForgeProfitHud.isForgeScreen("The Forge"));
        assertFalse(ForgeProfitHud.isForgeScreen("Forge Basin"));
        assertFalse(ForgeProfitHud.isForgeScreen("The Forge "));
    }

    @Test
    void usesOnlyForgeSlotsTenThroughSixteen() {
        assertArrayEquals(new int[]{10, 11, 12, 13, 14, 15, 16}, ForgeProfitHud.forgeSlots());
    }

    @Test
    void sumsOnlyThePotentialBazaarSellProfit() {
        ForgeProfitHud.View view = new ForgeProfitHud.View(List.of(
                new BazaarProfit.Item("REFINED_MITHRIL", "Refined Mithril", 2),
                new BazaarProfit.Item("REFINED_TITANIUM", "Refined Titanium", 3)
        ));

        BazaarProfit.Summary summary = BazaarProfit.summarize(view.items(), this::priceFor);
        HudContent content = ForgeProfitHud.content(view, summary);

        assertEquals(82.0, summary.total());
        assertTrue(lines(content).contains("Forge items sell for: 82 coins"));
    }

    @Test
    void marksTotalAsPartialWhenAnItemHasNoBazaarPrice() {
        ForgeProfitHud.View view = new ForgeProfitHud.View(List.of(
                new BazaarProfit.Item("REFINED_MITHRIL", "Refined Mithril", 2),
                new BazaarProfit.Item("NON_BAZAAR_ITEM", "Non Bazaar Item", 1)
        ));

        BazaarProfit.Summary summary = BazaarProfit.summarize(view.items(), this::priceFor);
        HudContent content = ForgeProfitHud.content(view, summary);

        assertEquals(22.0, summary.total());
        assertTrue(lines(content).contains("Forge items sell for: 22 coins+"));
        assertTrue(lines(content).contains("1 stack has no Bazaar price"));
    }

    @Test
    void showsOnlyTheLoadingStateWhileNoPriceIsKnownYet() {
        ForgeProfitHud.View view = new ForgeProfitHud.View(List.of(
                new BazaarProfit.Item("REFINED_MITHRIL", "Refined Mithril", 2)
        ));

        HudContent loading = ForgeProfitHud.content(view, new BazaarProfit.Summary(0.0, 0, 1, false, true, false));
        HudContent failed = ForgeProfitHud.content(view, new BazaarProfit.Summary(0.0, 0, 1, false, false, true));
        HudContent empty = ForgeProfitHud.content(new ForgeProfitHud.View(List.of()), BazaarProfit.Summary.empty());

        assertEquals(List.of("Loading Bazaar prices..."), lines(loading));
        assertEquals(List.of("Bazaar prices unavailable"), lines(failed));
        assertEquals(List.of("Forge is empty"), lines(empty));
    }

    @Test
    void staysHiddenAfterTheForgeCloses() {
        ForgeProfitHud.clear();

        assertFalse(ForgeProfitHud.Widgets.forgeProfit().visible());
    }

    private BazaarData.LookupResult priceFor(String itemId) {
        return switch (itemId) {
            case "REFINED_MITHRIL" -> result(11.0);
            case "REFINED_TITANIUM" -> result(20.0);
            default -> new BazaarData.LookupResult(null, false, false, false);
        };
    }

    private static BazaarData.LookupResult result(double sell) {
        return new BazaarData.LookupResult(new BazaarData.Product("test", sell + 1.0, sell), false, false, false);
    }

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(line -> line.getString()).toList();
    }
}
