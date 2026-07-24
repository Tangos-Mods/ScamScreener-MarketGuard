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
    void showsKnownValueAndUnpricedItems() {
        MinionProfitHud.View view = new MinionProfitHud.View(List.of(new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", 64)), 3_200.0);
        BazaarProfit.Summary summary = new BazaarProfit.Summary(1_234_567.0, 1, 12, false, false, false);
        HudContent content = MinionProfitHud.content(view, summary);

        assertTrue(lines(content).contains("Known Bazaar profit: 1,234,567 coins"));
        assertTrue(lines(content).contains("Held Coins: 3,200 coins"));
        assertTrue(lines(content).contains("12 stacks are missing a Bazaar price."));
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

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(line -> line.getString()).toList();
    }
}
