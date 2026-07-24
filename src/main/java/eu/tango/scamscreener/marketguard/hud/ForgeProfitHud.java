package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.BazaarProfit;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
public final class ForgeProfitHud {
    private static final String FORGE_TITLE = "The Forge";
    private static final int LAST_FORGE_SLOT = 16;
    private static final int[] FORGE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final long BAZAAR_LOOKUP_INTERVAL_MS = 1_000L;

    private static volatile View view = View.hidden();
    private static volatile long lastBazaarLookupAt;

    private ForgeProfitHud() {}

    public static void initialize() {
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, Widgets.class);
    }

    public static void update(AbstractContainerMenu menu, String title) {
        if (!isForgeScreen(title) || menu == null || menu.slots.size() <= LAST_FORGE_SLOT) {
            clear();
            return;
        }

        View next = new View(BazaarProfit.collectItems(menu, FORGE_SLOTS));
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

    public static boolean isForgeScreen(String title) {
        return FORGE_TITLE.equals(title);
    }

    static int[] forgeSlots() {
        return FORGE_SLOTS.clone();
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
                    .line(Component.literal("Forge Bazaar Profit"))
                    .visible(false)
                    .build();
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("title", Component.literal("Forge Bazaar Profit").withStyle(ChatFormatting.AQUA));

        if (current.items().isEmpty()) {
            lines.put("profit", Component.literal("No items in Forge slots.").withStyle(ChatFormatting.GRAY));
        }

        if (summary.pricedStacks() > 0) {
            String label = summary.missingStacks() == 0 ? "Potential Bazaar profit: " : "Known Bazaar profit: ";
            lines.put("profit", Component.literal(label + coins(summary.total())).withStyle(ChatFormatting.GOLD));
        }
        if (summary.missingStacks() > 0) {
            String suffix = summary.missingStacks() == 1 ? " stack is" : " stacks are";
            lines.put("missing", Component.literal(summary.missingStacks() + suffix + " missing a Bazaar price.")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (summary.loading() && summary.pricedStacks() == 0) {
            lines.put("loading", Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY));
        } else if (summary.refreshFailed() && summary.pricedStacks() == 0) {
            lines.put("unavailable", Component.literal("Bazaar prices are unavailable.").withStyle(ChatFormatting.RED));
        }
        if (summary.stale()) {
            lines.put("stale", Component.literal("Bazaar prices may be outdated.").withStyle(ChatFormatting.YELLOW));
        }
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.FORGE_PROFIT)) {
            Component line = lines.get(id);
            if (line != null) { content.line(line); added = true; }
        }
        return added ? content.build() : HudContent.builder().line(Component.literal("Forge Bazaar Profit")).visible(false).build();
    }

    private static String coins(double value) {
        return CoinFormat.coins(value);
    }

    record View(List<BazaarProfit.Item> items) {
        static View hidden() {
            return new View(null);
        }

        boolean visible() {
            return items != null;
        }
    }

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "forge_profit")
        public static HudContent forgeProfit() {
            refreshBazaarIfDue();
            if (!view.visible()) {
                return content(view, BazaarProfit.Summary.empty());
            }
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.FORGE_PROFIT)) {
                return HudContent.builder().line(Component.literal("Forge Bazaar Profit")).visible(false).build();
            }
            View current = view;
            return content(current, BazaarProfit.summarize(current.items(), BazaarData::lookupProduct));
        }
    }
}
