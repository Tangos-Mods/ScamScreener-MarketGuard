package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionReferencePrice;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.data.MarketRiskEvaluator;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AuctionPriceHud {
    private static final double CAUTION_OVER_REFERENCE = 0.05;
    private static final double EXTREME_DISCOUNT_BELOW_REFERENCE = 0.20;
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
        BazaarData.refreshAsyncIfNeeded();
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
        lines.put("item", Component.literal(current.displayName()).withStyle(ChatFormatting.WHITE));
        lines.put("auction", Component.literal("This auction: " + coins(current.auctionPrice())).withStyle(ChatFormatting.GOLD));

        LowestBinData.LookupResult lowestBin = current.lowestBin();
        if (lowestBin == null) {
            lines.put("lowest_bin", Component.literal("Loading reference price...").withStyle(ChatFormatting.GRAY));
            return build(lines);
        }

        Optional<AuctionReferencePrice> selected = AuctionReferencePrice.select(
                lowestBin.value(),
                lowestBin.average7d(),
                lowestBin.average30d()
        );
        if (selected.isEmpty()) {
            String status;
            if (lowestBin.loading()) {
                status = "Loading reference price...";
            } else if (lowestBin.refreshFailed()) {
                status = "Reference price unavailable.";
            } else {
                status = "No reference price data for this item.";
            }
            lines.put("lowest_bin", Component.literal(status).withStyle(ChatFormatting.GRAY));
        } else {
            AuctionReferencePrice reference = selected.orElseThrow();
            lines.put("lowest_bin", Component.literal(
                    "Reference: " + coins(reference.value()) + " (" + qualityLabel(reference) + ")"
            ).withStyle(qualityColor(reference.quality())));

            double difference = current.auctionPrice() - reference.value();
            double differencePercentage = difference / reference.value();
            lines.put("difference", Component.literal("Difference to reference: " + signedCoins(difference) + " (" + signedPercentage(differencePercentage) + ")")
                    .withStyle(difference > 0.0 ? ChatFormatting.RED : difference < 0.0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));

            if (!reference.safeForProtection()) {
                lines.put("advice", Component.literal("Price advice limited: low data quality.").withStyle(ChatFormatting.YELLOW));
            } else if (differencePercentage >= CAUTION_OVER_REFERENCE) {
                lines.put("advice", Component.literal("Caution: " + percentage(differencePercentage) + " above reference.")
                        .withStyle(ChatFormatting.YELLOW));
            } else if (differencePercentage <= -EXTREME_DISCOUNT_BELOW_REFERENCE) {
                lines.put("advice", Component.literal("Great deal: " + percentage(-differencePercentage) + " below reference.")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                lines.put("advice", Component.literal("Price is near the reference.").withStyle(ChatFormatting.GRAY));
            }
        }

        addRiskLines(lines, current.itemId(), lowestBin);
        if (lowestBin.stale()) {
            lines.put("stale", Component.literal("Price data may be outdated.").withStyle(ChatFormatting.YELLOW));
        }
        return build(lines);
    }

    private static void addRiskLines(Map<String, Component> lines, String itemId, LowestBinData.LookupResult lowestBin) {
        MarketRiskEvaluator.Warning volatility = MarketRiskEvaluator.auctionVolatility(
                lowestBin.average7d(),
                lowestBin.average30d()
        );
        if (volatility != null) {
            lines.put("volatility", warningLine(volatility, false));
        }

        BazaarData.LookupResult bazaar = BazaarData.lookupProduct(itemId);
        MarketRiskEvaluator.Warning liquidity = MarketRiskEvaluator.bazaarLiquidity(bazaar.value());
        if (liquidity != null) {
            lines.put("liquidity", warningLine(liquidity, bazaar.stale()));
        }
    }

    private static Component warningLine(MarketRiskEvaluator.Warning warning, boolean stale) {
        String text = warning.text() + (stale ? " Bazaar data may be outdated." : "");
        return Component.literal(text).withStyle(warning.highRisk() ? ChatFormatting.RED : ChatFormatting.YELLOW);
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

    private static String qualityLabel(AuctionReferencePrice reference) {
        String signals = reference.signalCount() == 1 ? "1 signal" : reference.signalCount() + " signals";
        return reference.quality().name().toLowerCase(Locale.ROOT) + " quality, " + signals;
    }

    private static ChatFormatting qualityColor(AuctionReferencePrice.Quality quality) {
        return switch (quality) {
            case HIGH -> ChatFormatting.GREEN;
            case MEDIUM -> ChatFormatting.YELLOW;
            case LOW -> ChatFormatting.RED;
        };
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
