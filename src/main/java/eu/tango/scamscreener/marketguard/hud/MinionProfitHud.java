package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.BazaarProfit;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

public final class MinionProfitHud {
    private static final Pattern MINION_TITLE = Pattern.compile("(?i)^.*\\bminion\\s+[ivxlcdm]+\\s*$");
    private static final Pattern HELD_COINS = Pattern.compile("(?i)^held coins:\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?:coins)?$");
    private static final int HELD_COINS_SLOT = 28;
    private static final int[] STORAGE_SLOTS = {
            20, 21, 22, 23, 24, 25,
            29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42, 43
    };
    private static final long BAZAAR_LOOKUP_INTERVAL_MS = 1_000L;
    private static final long MIN_FORECAST_OBSERVATION_MS = 60_000L;

    private static volatile View view = View.hidden();
    private static volatile Observation observation;
    private static volatile long lastBazaarLookupAt;

    private MinionProfitHud() {}

    public static void initialize() {
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, Widgets.class);
    }

    public static void update(AbstractContainerMenu menu, String title) {
        if (!isMinionScreen(title) || menu == null) {
            clear();
            return;
        }

        View next = new View(title, BazaarProfit.collectItems(menu, STORAGE_SLOTS), heldCoins(menu));
        if (!view.visible() || !view.title().equals(next.title())) {
            observation = new Observation(next, System.currentTimeMillis());
        }
        if (!next.equals(view)) {
            view = next;
            lastBazaarLookupAt = 0L;
        }
        refreshBazaarIfDue();
    }

    public static void clear() {
        view = View.hidden();
        observation = null;
        lastBazaarLookupAt = 0L;
    }

    public static boolean isMinionScreen(String title) {
        return title != null && MINION_TITLE.matcher(title).matches();
    }

    static int[] storageSlots() {
        return STORAGE_SLOTS.clone();
    }

    public static double heldCoins(ItemStack stack) {
        Double heldCoins = findHeldCoins(stack);
        return heldCoins == null ? 0.0 : heldCoins;
    }

    static double heldCoins(List<String> loreLines) {
        Double heldCoins = findHeldCoins(loreLines);
        return heldCoins == null ? 0.0 : heldCoins;
    }

    private static void refreshBazaarIfDue() {
        if (!view.visible() || System.currentTimeMillis() - lastBazaarLookupAt < BAZAAR_LOOKUP_INTERVAL_MS) {
            return;
        }

        lastBazaarLookupAt = System.currentTimeMillis();
        BazaarData.refreshAsyncIfNeeded();
    }

    static HudContent content(View current, BazaarProfit.Summary summary, Forecast forecast) {
        if (!current.visible()) {
            return HudContent.builder()
                    .line(Component.literal("Minion Profit"))
                    .visible(false)
                    .build();
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        if (current.heldCoins() != null) {
            lines.put("held_coins", Component.literal("Held Coins: " + coins(current.heldCoins())).withStyle(ChatFormatting.GOLD));
        }

        if (current.items().isEmpty()) {
            lines.put("profit", Component.literal("No items in Minion storage.").withStyle(ChatFormatting.GRAY));
        }

        if (summary.pricedStacks() > 0) {
            String label = summary.missingStacks() == 0 ? "Potential Bazaar profit: " : "Known Bazaar profit: ";
            lines.put("profit", Component.literal(label + coins(summary.total())).withStyle(ChatFormatting.GOLD));
        }
        if (forecast.state() == ForecastState.AVAILABLE) {
            lines.put("forecast", Component.translatable(
                    "marketguard.hud.minion.forecast.available",
                    coins(forecast.coinsPerHour()),
                    coins(forecast.coinsPerDay())
            ).withStyle(ChatFormatting.GREEN));
            lines.put("forecast_status", Component.translatable(
                    "marketguard.hud.minion.forecast.basis",
                    duration(forecast.observedMs())
            ).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            String key = switch (forecast.state()) {
                case OBSERVING -> "marketguard.hud.minion.forecast.observing";
                case NO_INCREASE -> "marketguard.hud.minion.forecast.no_increase";
                case WAITING_FOR_PRICES -> "marketguard.hud.minion.forecast.waiting_prices";
                case RESTARTED -> "marketguard.hud.minion.forecast.restarted";
                case AVAILABLE -> throw new IllegalStateException("Available forecast handled above");
            };
            Object[] args = switch (forecast.state()) {
                case OBSERVING, NO_INCREASE -> new Object[]{duration(forecast.observedMs())};
                default -> new Object[0];
            };
            lines.put("forecast_status", Component.translatable(key, args).withStyle(ChatFormatting.GRAY));
        }
        if (summary.missingStacks() > 0) {
            String suffix = summary.missingStacks() == 1 ? " stack is" : " stacks are";
            lines.put("missing", Component.literal(summary.missingStacks() + suffix + " missing a Bazaar price.")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (summary.loading() && summary.pricedStacks() == 0) {
            lines.put("loading", Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY));
        } else if (summary.refreshFailed() && summary.pricedStacks() == 0) {
            lines.put("unavailable", Component.literal("Bazaar prices unavailable.").withStyle(ChatFormatting.RED));
        }
        if (summary.stale()) {
            lines.put("stale", Component.literal("Bazaar prices may be outdated.").withStyle(ChatFormatting.YELLOW));
        }
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.MINION_PROFIT)) {
            Component line = lines.get(id);
            if (line != null) { content.line(line); added = true; }
        }
        return added ? content.build() : HudContent.builder().line(Component.literal("Minion Profit")).visible(false).build();
    }

    private static String coins(double value) {
        return eu.tango.scamscreener.marketguard.util.CoinFormat.format(value) + " coins";
    }

    private static String duration(long millis) {
        long seconds = Math.max(0L, millis / 1_000L);
        if (seconds < 60L) {
            return seconds + "s";
        }
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    static Forecast estimate(
            Observation baseline,
            View current,
            Function<String, BazaarData.LookupResult> lookup,
            long nowMs
    ) {
        long observedMs = Math.max(0L, nowMs - baseline.observedAtMs());
        BazaarProfit.ValueDelta storageDelta = BazaarProfit.valueDelta(
                baseline.view().items(),
                current.items(),
                lookup
        );
        if (storageDelta.missingItems() > 0) {
            return new Forecast(ForecastState.WAITING_FOR_PRICES, 0.0, 0.0, observedMs);
        }

        double heldCoinsDelta = baseline.view().heldCoins() != null && current.heldCoins() != null
                ? current.heldCoins() - baseline.view().heldCoins()
                : 0.0;
        double valueDelta = storageDelta.total() + heldCoinsDelta;
        if (valueDelta < -0.5) {
            return new Forecast(ForecastState.RESTARTED, 0.0, 0.0, observedMs);
        }
        if (observedMs < MIN_FORECAST_OBSERVATION_MS) {
            return new Forecast(ForecastState.OBSERVING, 0.0, 0.0, observedMs);
        }
        if (valueDelta <= 0.5) {
            return new Forecast(ForecastState.NO_INCREASE, 0.0, 0.0, observedMs);
        }

        double coinsPerHour = valueDelta * 3_600_000.0 / observedMs;
        return new Forecast(ForecastState.AVAILABLE, coinsPerHour, coinsPerHour * 24.0, observedMs);
    }

    private static synchronized Forecast currentForecast(
            View current,
            Function<String, BazaarData.LookupResult> lookup,
            long nowMs
    ) {
        Observation baseline = observation;
        if (baseline == null || !baseline.view().title().equals(current.title())) {
            observation = new Observation(current, nowMs);
            return new Forecast(ForecastState.OBSERVING, 0.0, 0.0, 0L);
        }

        Forecast forecast = estimate(baseline, current, lookup, nowMs);
        if (forecast.state() == ForecastState.RESTARTED) {
            observation = new Observation(current, nowMs);
        }
        return forecast;
    }

    private static Double heldCoins(AbstractContainerMenu menu) {
        if (menu.slots.size() <= HELD_COINS_SLOT) {
            return null;
        }
        return findHeldCoins(menu.getSlot(HELD_COINS_SLOT).getItem());
    }

    private static Double findHeldCoins(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }
        List<String> loreLines = new ArrayList<>();
        for (Component line : lore.lines()) {
            loreLines.add(line.getString());
        }
        return findHeldCoins(loreLines);
    }

    private static Double findHeldCoins(List<String> loreLines) {
        for (String line : loreLines) {
            Matcher matcher = HELD_COINS.matcher(line.trim());
            if (matcher.matches()) {
                return Double.parseDouble(matcher.group(1).replace(",", ""));
            }
        }
        return null;
    }

    record View(String title, List<BazaarProfit.Item> items, Double heldCoins) {
        static View hidden() {
            return new View(null, null, null);
        }

        boolean visible() {
            return items != null;
        }
    }

    record Observation(View view, long observedAtMs) {}

    enum ForecastState {
        OBSERVING,
        AVAILABLE,
        NO_INCREASE,
        WAITING_FOR_PRICES,
        RESTARTED
    }

    record Forecast(ForecastState state, double coinsPerHour, double coinsPerDay, long observedMs) {
        static Forecast observing() {
            return new Forecast(ForecastState.OBSERVING, 0.0, 0.0, 0L);
        }
    }

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "minion_profit")
        public static HudContent minionProfit() {
            refreshBazaarIfDue();
            if (!view.visible()) {
                return content(view, BazaarProfit.Summary.empty(), Forecast.observing());
            }
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.MINION_PROFIT)) {
                return HudContent.builder().line(Component.literal("Minion Profit")).visible(false).build();
            }
            View current = view;
            BazaarProfit.Summary summary = BazaarProfit.summarize(current.items(), BazaarData::lookupProduct);
            Forecast forecast = currentForecast(current, BazaarData::lookupProduct, System.currentTimeMillis());
            return content(current, summary, forecast);
        }
    }
}
