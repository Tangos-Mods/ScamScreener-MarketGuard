package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.data.BazaarProfit;
import eu.tango.tangosHudLib.api.HudContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinionProfitHudTest {

    @Test
    void recognizesEveryMinionScreen() {
        assertTrue(MinionProfitHud.isMinionScreen("Gold Minion X"));
        assertTrue(MinionProfitHud.isMinionScreen("Inferno Minion XI"));
        assertTrue(MinionProfitHud.isMinionScreen("Tarantula Minion XI"));
        assertTrue(MinionProfitHud.isMinionScreen("Voidling Minion XIII "));
        assertTrue(MinionProfitHud.isMinionScreen("Minion I"));
        assertFalse(MinionProfitHud.isMinionScreen("The Forge"));
        assertFalse(MinionProfitHud.isMinionScreen("Auction House"));
    }

    @Test
    void usesOnlyTheMinionStorageSlots() {
        assertArrayEquals(new int[]{
                20, 21, 22, 23, 24, 25,
                29, 30, 31, 32, 33, 34,
                38, 39, 40, 41, 42, 43
        }, MinionProfitHud.storageSlots());
    }

    @Test
    void showsStorageValueWithUnpricedStacks() {
        BazaarProfit.Summary summary = new BazaarProfit.Summary(1_234_567.0, 1, 12, false, false, false);
        HudContent content = MinionProfitHud.content(view(64, 3_200.0), summary);

        assertEquals(List.of(
                "Held coins: 3,200",
                "Storage sells for: 1,234,567+",
                "12 stacks have no Bazaar price"
        ), lines(content));
    }

    @Test
    void showsStorageValueWhenEveryStackIsPriced() {
        BazaarProfit.Summary summary = new BazaarProfit.Summary(320.0, 1, 0, false, false, false);
        HudContent content = MinionProfitHud.content(view(64, null), summary);

        assertEquals(List.of("Storage sells for: 320"), lines(content));
    }

    @Test
    void usesSingularForOneUnpricedStack() {
        BazaarProfit.Summary summary = new BazaarProfit.Summary(320.0, 1, 1, false, false, false);
        HudContent content = MinionProfitHud.content(view(64, null), summary);

        assertTrue(lines(content).contains("1 stack has no Bazaar price"));
    }

    @Test
    void showsEmptyStorage() {
        MinionProfitHud.View view = new MinionProfitHud.View("Gold Minion X", List.of(), 3_200.0);
        HudContent content = MinionProfitHud.content(view, BazaarProfit.Summary.empty());

        assertEquals(List.of("Held coins: 3,200", "Storage is empty"), lines(content));
    }

    @Test
    void showsLoadingUntilAnyStackIsPriced() {
        BazaarProfit.Summary loading = new BazaarProfit.Summary(0.0, 0, 1, false, true, false);
        BazaarProfit.Summary priced = new BazaarProfit.Summary(320.0, 1, 0, false, true, false);

        assertEquals(List.of("Loading Bazaar prices..."), lines(MinionProfitHud.content(view(64, null), loading)));
        assertEquals(List.of("Storage sells for: 320"), lines(MinionProfitHud.content(view(64, null), priced)));
    }

    @Test
    void showsUnavailableWhenTheRefreshFailedWithoutPrices() {
        BazaarProfit.Summary summary = new BazaarProfit.Summary(0.0, 0, 1, false, false, true);
        HudContent content = MinionProfitHud.content(view(64, null), summary);

        assertEquals(List.of("Bazaar prices unavailable"), lines(content));
    }

    @Test
    void warnsAboutOutdatedPrices() {
        BazaarProfit.Summary summary = new BazaarProfit.Summary(320.0, 1, 0, true, false, false);
        HudContent content = MinionProfitHud.content(view(64, null), summary);

        assertEquals(List.of("Storage sells for: 320", "Prices may be outdated"), lines(content));
    }

    @Test
    void hidesOutsideAMinionScreen() {
        HudContent content = MinionProfitHud.content(MinionProfitHud.View.hidden(), BazaarProfit.Summary.empty());

        assertFalse(content.visible());
    }

    @Test
    void readsHeldCoinsFromTheHopperLore() {
        assertEquals(1_234_567.0, MinionProfitHud.heldCoins(List.of("Storage", "Held Coins: 1,234,567")));
        assertEquals(0.0, MinionProfitHud.heldCoins(List.of("Storage")));
    }

    private MinionProfitHud.View view(int count, Double heldCoins) {
        return new MinionProfitHud.View(
                "Gold Minion X",
                List.of(new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", count)),
                heldCoins
        );
    }

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(line -> line.getString()).toList();
    }
}
