package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionReferencePrice;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.data.MarketRiskEvaluator;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AuctionPriceHud {
    private static final double FAIR_ABOVE_MARKET = 0.05;
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
            lines.put("lowest_bin", Component.literal("Loading market price...").withStyle(ChatFormatting.GRAY));
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
                status = "Loading market price...";
            } else if (lowestBin.refreshFailed()) {
                status = "Market price unavailable";
            } else {
                status = "No market price for this item";
            }
            lines.put("lowest_bin", Component.literal(status).withStyle(ChatFormatting.GRAY));
        } else {
            AuctionReferencePrice reference = selected.orElseThrow();
            boolean reliable = reference.safeForProtection();
            lines.put("lowest_bin", Component.literal("Market price: ~" + coins(reference.value()) + (reliable ? "" : " (few recent sales)"))
                    .withStyle(reliable ? ChatFormatting.GRAY : ChatFormatting.YELLOW));

            double difference = current.auctionPrice() - reference.value();
            double differencePercentage = difference / reference.value();
            boolean cheapest = lowestBin.value() != null && current.auctionPrice() <= lowestBin.value();
            Component verdict = verdict(differencePercentage, reliable, cheapest);
            lines.put("advice", verdict);
            lines.put("difference", Component.literal(signedCoins(difference) + " (" + signedPercentage(differencePercentage) + ") vs. market")
                    .withStyle(verdict.getStyle()));
        }

        addRiskLines(lines, current.itemId(), lowestBin);
        if (lowestBin.stale()) {
            lines.put("stale", Component.literal("Prices may be outdated").withStyle(ChatFormatting.YELLOW));
        }
        return build(lines);
    }

    private static Component verdict(double differencePercentage, boolean reliable, boolean cheapest) {
        int overThreshold = AuctionOverbidding.isEnabled() ? AuctionOverbidding.getThreshold() : AuctionOverbidding.DEFAULT_THRESHOLD;
        int underThreshold = AuctionUnderbidding.isEnabled() ? AuctionUnderbidding.getThreshold() : AuctionUnderbidding.DEFAULT_THRESHOLD;
        double over = overThreshold / 100.0 - 1.0;
        double under = 1.0 - underThreshold / 100.0;
        String percent = percentage(Math.abs(differencePercentage));
        if (cheapest) {
            if (reliable && differencePercentage >= over) {
                return Component.literal("Cheapest BIN, but " + percent + " above market - overpriced").withStyle(ChatFormatting.RED);
            }
            String market = Math.abs(differencePercentage) < FAIR_ABOVE_MARKET ? ""
                    : ", " + percent + (differencePercentage < 0.0 ? " below market" : " above market");
            return Component.literal("Cheapest BIN right now" + market).withStyle(ChatFormatting.GREEN);
        }
        if (differencePercentage <= -under) {
            return reliable
                    ? Component.literal("Good deal: " + percent + " below market").withStyle(ChatFormatting.GREEN)
                    : Component.literal("Roughly " + percent + " below market").withStyle(ChatFormatting.YELLOW);
        }
        if (differencePercentage < Math.min(FAIR_ABOVE_MARKET, over)) {
            return Component.literal("Fair price").withStyle(ChatFormatting.GREEN);
        }
        if (!reliable) {
            return Component.literal("Roughly " + percent + " above market").withStyle(ChatFormatting.YELLOW);
        }
        if (differencePercentage >= over) {
            return Component.literal(percent + " above market - overpriced").withStyle(ChatFormatting.RED);
        }
        return Component.literal(percent + " above market").withStyle(ChatFormatting.YELLOW);
    }

    private static void addRiskLines(Map<String, Component> lines, String itemId, LowestBinData.LookupResult lowestBin) {
        MarketRiskEvaluator.Warning volatility = MarketRiskEvaluator.auctionVolatility(
                lowestBin.average7d(),
                lowestBin.average30d()
        );
        if (volatility != null && volatility.highRisk()) {
            lines.put("volatility", Component.literal(volatility.text()).withStyle(ChatFormatting.YELLOW));
        }

        BazaarData.LookupResult bazaar = BazaarData.lookupProduct(itemId);
        MarketRiskEvaluator.Warning liquidity = MarketRiskEvaluator.bazaarLiquidity(bazaar.value());
        if (liquidity != null && liquidity.highRisk()) {
            String text = liquidity.text() + (bazaar.stale() ? " (Bazaar data may be outdated)" : "");
            lines.put("liquidity", Component.literal(text).withStyle(ChatFormatting.YELLOW));
        }
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
        return Math.round(value * 100.0) + "%";
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
