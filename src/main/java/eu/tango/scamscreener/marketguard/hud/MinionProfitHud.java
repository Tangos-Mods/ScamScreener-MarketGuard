package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.BazaarProfit;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
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

    private static volatile View view = View.hidden();
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
        if (!next.equals(view)) {
            view = next;
            lastBazaarLookupAt = 0L;
        }
        refreshBazaarIfDue();
    }

    public static void clear() {
        view = View.hidden();
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

    static HudContent content(View current, BazaarProfit.Summary summary) {
        if (!current.visible()) {
            return HudContent.builder()
                    .line(Component.literal("Minion Profit"))
                    .visible(false)
                    .build();
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        if (current.heldCoins() != null) {
            lines.put("held_coins", Component.literal("Held coins: " + coins(current.heldCoins())).withStyle(ChatFormatting.GOLD));
        }
        if (current.items().isEmpty()) {
            lines.put("profit", Component.literal("Storage is empty").withStyle(ChatFormatting.GRAY));
        } else if (summary.pricedStacks() > 0) {
            String suffix = summary.missingStacks() > 0 ? "+" : "";
            lines.put("profit", Component.literal("Storage sells for: " + coins(summary.total()) + suffix).withStyle(ChatFormatting.GOLD));
        }
        boolean noPricesYet = summary.pricedStacks() == 0 && (summary.loading() || summary.refreshFailed());
        if (summary.missingStacks() > 0 && !noPricesYet) {
            String stacks = summary.missingStacks() == 1 ? " stack has" : " stacks have";
            lines.put("missing", Component.literal(summary.missingStacks() + stacks + " no Bazaar price").withStyle(ChatFormatting.GRAY));
        }
        if (noPricesYet && summary.loading()) {
            lines.put("loading", Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY));
        } else if (noPricesYet) {
            lines.put("unavailable", Component.literal("Bazaar prices unavailable").withStyle(ChatFormatting.RED));
        }
        if (summary.stale()) {
            lines.put("stale", Component.literal("Prices may be outdated").withStyle(ChatFormatting.YELLOW));
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
        return CoinFormat.format(value);
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

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "minion_profit")
        public static HudContent minionProfit() {
            refreshBazaarIfDue();
            if (!view.visible()) {
                return content(view, BazaarProfit.Summary.empty());
            }
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.MINION_PROFIT)) {
                return HudContent.builder().line(Component.literal("Minion Profit")).visible(false).build();
            }
            View current = view;
            return content(current, BazaarProfit.summarize(current.items(), BazaarData::lookupProduct));
        }
    }
}
