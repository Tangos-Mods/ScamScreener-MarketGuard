package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.data.BazaarProfit;
import eu.tango.scamscreener.marketguard.data.BazaarData;
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
        MinionProfitHud.View view = view(64, 3_200.0);
        BazaarProfit.Summary summary = new BazaarProfit.Summary(1_234_567.0, 1, 12, false, false, false);
        HudContent content = MinionProfitHud.content(view, summary, MinionProfitHud.Forecast.observing());

        assertTrue(lines(content).contains("Known Bazaar profit: 1,234,567 coins"));
        assertTrue(lines(content).contains("Held Coins: 3,200 coins"));
        assertTrue(lines(content).contains("12 stacks are missing a Bazaar price."));
    }

    @Test
    void hidesOutsideAMinionScreen() {
        HudContent content = MinionProfitHud.content(
                MinionProfitHud.View.hidden(),
                BazaarProfit.Summary.empty(),
                MinionProfitHud.Forecast.observing()
        );

        assertFalse(content.visible());
    }

    @Test
    void readsHeldCoinsFromTheHopperLore() {
        assertEquals(1_234_567.0, MinionProfitHud.heldCoins(List.of("Storage", "Held Coins: 1,234,567")));
        assertEquals(0.0, MinionProfitHud.heldCoins(List.of("Storage")));
    }

    @Test
    void forecastsOnlyAfterAFullMinuteOfVisibleProduction() {
        MinionProfitHud.Observation baseline = new MinionProfitHud.Observation(view(64, 1_000.0), 1_000L);

        MinionProfitHud.Forecast early = MinionProfitHud.estimate(baseline, view(74, 1_000.0), this::priceFor, 31_000L);
        MinionProfitHud.Forecast ready = MinionProfitHud.estimate(baseline, view(74, 1_000.0), this::priceFor, 61_000L);

        assertEquals(MinionProfitHud.ForecastState.OBSERVING, early.state());
        assertEquals(MinionProfitHud.ForecastState.AVAILABLE, ready.state());
        assertEquals(3_000.0, ready.coinsPerHour());
        assertEquals(72_000.0, ready.coinsPerDay());
    }

    @Test
    void includesObservedHopperCoinsInTheForecast() {
        MinionProfitHud.Observation baseline = new MinionProfitHud.Observation(view(64, 1_000.0), 1_000L);

        MinionProfitHud.Forecast forecast = MinionProfitHud.estimate(
                baseline,
                view(64, 1_100.0),
                this::priceFor,
                61_000L
        );

        assertEquals(MinionProfitHud.ForecastState.AVAILABLE, forecast.state());
        assertEquals(6_000.0, forecast.coinsPerHour());
    }

    @Test
    void restartsObservationWhenStorageValueDrops() {
        MinionProfitHud.Observation baseline = new MinionProfitHud.Observation(view(74, 1_000.0), 1_000L);

        MinionProfitHud.Forecast forecast = MinionProfitHud.estimate(
                baseline,
                view(64, 1_000.0),
                this::priceFor,
                61_000L
        );

        assertEquals(MinionProfitHud.ForecastState.RESTARTED, forecast.state());
    }

    @Test
    void waitsForABazaarPriceForNewItems() {
        MinionProfitHud.Observation baseline = new MinionProfitHud.Observation(view(64, 1_000.0), 1_000L);
        MinionProfitHud.View current = new MinionProfitHud.View("Gold Minion X", List.of(
                new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", 64),
                new BazaarProfit.Item("UNKNOWN", "Unknown", 1)
        ), 1_000.0);

        MinionProfitHud.Forecast forecast = MinionProfitHud.estimate(baseline, current, this::priceFor, 61_000L);

        assertEquals(MinionProfitHud.ForecastState.WAITING_FOR_PRICES, forecast.state());
    }

    private MinionProfitHud.View view(int count, Double heldCoins) {
        return new MinionProfitHud.View(
                "Gold Minion X",
                List.of(new BazaarProfit.Item("GOLD_INGOT", "Gold Ingot", count)),
                heldCoins
        );
    }

    private BazaarData.LookupResult priceFor(String itemId) {
        if (!"GOLD_INGOT".equals(itemId)) {
            return new BazaarData.LookupResult(null, false, false, false);
        }
        return new BazaarData.LookupResult(
                new BazaarData.Product("Gold Ingot", 6.0, 5.0),
                false,
                false,
                false
        );
    }

    private static List<String> lines(HudContent content) {
        return content.lines().stream().map(line -> line.getString()).toList();
    }
}
