package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuctionPriceHud {
    private static final double CAUTION_OVER_LOWEST_BIN = 0.05;
    private static final double EXTREME_DISCOUNT_BELOW_LOWEST_BIN = 0.20;
    private static final long LOWEST_BIN_LOOKUP_INTERVAL_MS = 1_000L;

    private static volatile View view = View.hidden();
    private static volatile long lastLowestBinLookupAt;

    private AuctionPriceHud() {}

    public static void initialize() {
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, Widgets.class);
    }

    public static void update(String itemId, String displayName, double auctionPrice) {
        view = new View(itemId, displayName, auctionPrice, null);
        lastLowestBinLookupAt = 0L;
    }

    public static void clear() {
        view = View.hidden();
        lastLowestBinLookupAt = 0L;
    }

    private static void refreshLowestBinIfDue() {
        View current = view;
        if (!current.visible() || System.currentTimeMillis() - lastLowestBinLookupAt < LOWEST_BIN_LOOKUP_INTERVAL_MS) {
            return;
        }

        lastLowestBinLookupAt = System.currentTimeMillis();
        LowestBinData.refreshAsyncIfNeeded();
        LowestBinData.LookupResult lowestBin = LowestBinData.lookupLowestBin(current.itemId());
        if (!lowestBin.equals(current.lowestBin())) {
            view = new View(current.itemId(), current.displayName(), current.auctionPrice(), lowestBin);
        }
    }

    static HudContent content(View current) {
        if (!current.visible()) {
            return HudContent.builder()
                    .line(Component.literal("Auction Price"))
                    .visible(false)
                    .build();
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("title", Component.literal("Auction Price").withStyle(ChatFormatting.AQUA));
        lines.put("item", Component.literal(current.displayName()).withStyle(ChatFormatting.WHITE));
        lines.put("auction", Component.literal("This auction: " + coins(current.auctionPrice())).withStyle(ChatFormatting.GOLD));

        LowestBinData.LookupResult lowestBin = current.lowestBin();
        if (lowestBin == null) {
            lines.put("lowest_bin", Component.literal("Loading Lowest BIN...").withStyle(ChatFormatting.GRAY));
            return build(lines);
        }

        if (!lowestBin.hasValue()) {
            String status;
            if (lowestBin.loading()) {
                status = "Loading Lowest BIN...";
            } else if (lowestBin.refreshFailed()) {
                status = "Lowest BIN unavailable.";
            } else {
                status = "No Lowest BIN data for this item.";
            }
            lines.put("lowest_bin", Component.literal(status).withStyle(ChatFormatting.GRAY));
        } else {
            lines.put("lowest_bin", Component.literal("Lowest BIN: " + coins(lowestBin.value())).withStyle(ChatFormatting.YELLOW));
        }

        if (lowestBin.average7d() == null) {
            lines.put("difference", Component.literal("7d average unavailable.").withStyle(ChatFormatting.GRAY));
        } else {
            double difference = current.auctionPrice() - lowestBin.average7d();
            double differencePercentage = difference / lowestBin.average7d();
            lines.put("difference", Component.literal("Difference to 7d avg: " + signedCoins(difference) + " (" + signedPercentage(differencePercentage) + ")")
                    .withStyle(difference > 0.0 ? ChatFormatting.RED : difference < 0.0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }

        if (lowestBin.hasValue()) {
            double lowestBinDifferencePercentage = (current.auctionPrice() - lowestBin.value()) / lowestBin.value();
            if (lowestBinDifferencePercentage >= CAUTION_OVER_LOWEST_BIN) {
                lines.put("advice", Component.literal("Caution: " + percentage(lowestBinDifferencePercentage) + " above Lowest BIN.")
                        .withStyle(ChatFormatting.YELLOW));
            } else if (lowestBinDifferencePercentage <= -EXTREME_DISCOUNT_BELOW_LOWEST_BIN) {
                lines.put("advice", Component.literal("Great deal: " + percentage(-lowestBinDifferencePercentage) + " below Lowest BIN.")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                lines.put("advice", Component.literal("Price is near Lowest BIN.").withStyle(ChatFormatting.GRAY));
            }
        }

        if (lowestBin.stale()) {
            lines.put("stale", Component.literal("Lowest BIN data may be outdated.").withStyle(ChatFormatting.YELLOW));
        }
        return build(lines);
    }

    private static HudContent build(Map<String, Component> lines) {
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.AUCTION_PRICE)) {
            Component line = lines.get(id);
            if (line != null) { content.line(line); added = true; }
        }
        return added ? content.build() : HudContent.builder().line(Component.literal("Auction Price")).visible(false).build();
    }

    private static String coins(double value) {
        return CoinFormat.coins(value);
    }

    private static String signedCoins(double value) {
        return (value > 0.0 ? "+" : value < 0.0 ? "-" : "") + coins(Math.abs(value));
    }

    private static String percentage(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0);
    }

    private static String signedPercentage(double value) {
        return (value > 0.0 ? "+" : value < 0.0 ? "-" : "") + percentage(Math.abs(value));
    }

    record View(String itemId, String displayName, double auctionPrice, LowestBinData.LookupResult lowestBin) {
        static View hidden() {
            return new View(null, null, 0.0, null);
        }

        boolean visible() {
            return itemId != null && !itemId.isBlank() && displayName != null && !displayName.isBlank() && auctionPrice > 0.0;
        }
    }

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "auction_price")
        public static HudContent auctionPrice() {
            refreshLowestBinIfDue();
            if (!view.visible()) {
                return content(view);
            }
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.AUCTION_PRICE)) {
                return HudContent.builder().line(Component.literal("Auction Price")).visible(false).build();
            }
            return content(view);
        }
    }
}
