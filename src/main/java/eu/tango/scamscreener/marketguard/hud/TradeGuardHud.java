package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionReferencePrice;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.screen.HypixelScreens;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class TradeGuardHud {
    private static final int[] OWN_OFFER_SLOTS = {
            0, 1, 2, 3,
            9, 10, 11, 12,
            18, 19, 20, 21,
            27, 28, 29, 30
    };
    private static final int[] PARTNER_OFFER_SLOTS = {
            5, 6, 7, 8,
            14, 15, 16, 17,
            23, 24, 25, 26,
            32, 33, 34, 35
    };
    private static final int LAST_OFFER_SLOT = 35;
    private static final long PRICE_REFRESH_INTERVAL_MS = 1_000L;

    private static volatile View view = View.hidden();
    private static volatile long lastPriceRefreshAt;

    private TradeGuardHud() {}

    public static void initialize() {
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, Widgets.class);
    }

    public static void update(AbstractContainerMenu menu, String title) {
        if (!HypixelScreens.isTrade(title) || menu == null || menu.slots.size() <= LAST_OFFER_SLOT) {
            clear();
            return;
        }

        View next = new View(collect(menu, OWN_OFFER_SLOTS), collect(menu, PARTNER_OFFER_SLOTS));
        if (!next.equals(view)) {
            view = next;
            lastPriceRefreshAt = 0L;
        }
        refreshPricesIfDue();
    }

    public static void clear() {
        view = View.hidden();
        lastPriceRefreshAt = 0L;
    }

    static int[] ownOfferSlots() {
        return OWN_OFFER_SLOTS.clone();
    }

    static int[] partnerOfferSlots() {
        return PARTNER_OFFER_SLOTS.clone();
    }

    private static Offer collect(AbstractContainerMenu menu, int[] slots) {
        List<OfferItem> items = new ArrayList<>();
        int unidentifiedStacks = 0;
        for (int slot : slots) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String itemId = SkyBlockItemUtil.getSkyblockId(stack);
            if (itemId == null || itemId.isBlank()) {
                unidentifiedStacks++;
            } else {
                items.add(new OfferItem(itemId, stack.getCount()));
            }
        }
        return new Offer(List.copyOf(items), unidentifiedStacks);
    }

    private static void refreshPricesIfDue() {
        if (!view.visible() || !view.hasItems()
                || System.currentTimeMillis() - lastPriceRefreshAt < PRICE_REFRESH_INTERVAL_MS) {
            return;
        }

        lastPriceRefreshAt = System.currentTimeMillis();
        BazaarData.refreshAsyncIfNeeded();
        LowestBinData.refreshAsyncIfNeeded();
    }

    static Evaluation evaluate(
            Offer own,
            Offer partner,
            Function<String, PriceQuote> prices,
            int minimumReceivedPercentage,
            long absoluteWarningThreshold
    ) {
        Map<String, PriceQuote> priceCache = new HashMap<>();
        SideValue ownValue = value(own, itemId -> priceCache.computeIfAbsent(itemId, prices));
        SideValue partnerValue = value(partner, itemId -> priceCache.computeIfAbsent(itemId, prices));
        double difference = partnerValue.total() - ownValue.total();

        boolean complete = ownValue.reliableForWarning() && partnerValue.reliableForWarning();
        boolean thresholdEnabled = minimumReceivedPercentage > 0 && minimumReceivedPercentage < 100;
        double minimumReceived = ownValue.total() * minimumReceivedPercentage / 100.0;
        boolean warning = complete
                && thresholdEnabled
                && ownValue.total() > 0.0
                && partnerValue.total() < minimumReceived
                && -difference >= Math.max(0L, absoluteWarningThreshold);
        double disadvantagePercentage = ownValue.total() <= 0.0
                ? 0.0
                : Math.max(0.0, -difference / ownValue.total());
        return new Evaluation(ownValue, partnerValue, difference, warning, disadvantagePercentage);
    }

    private static SideValue value(Offer offer, Function<String, PriceQuote> prices) {
        double total = 0.0;
        int pricedStacks = 0;
        int unpricedStacks = offer.unidentifiedStacks();
        int lowConfidenceStacks = 0;
        boolean stale = false;
        boolean loading = false;
        boolean refreshFailed = false;
        for (OfferItem item : offer.items()) {
            PriceQuote quote = prices.apply(item.itemId());
            if (quote == null) {
                unpricedStacks++;
                continue;
            }
            stale |= quote.stale();
            loading |= quote.loading();
            refreshFailed |= quote.refreshFailed();
            if (!quote.hasValue()) {
                unpricedStacks++;
                continue;
            }

            total += quote.value() * item.count();
            pricedStacks++;
            if (!quote.reliable()) {
                lowConfidenceStacks++;
            }
        }
        return new SideValue(
                total,
                pricedStacks,
                unpricedStacks,
                lowConfidenceStacks,
                stale,
                loading,
                refreshFailed
        );
    }

    static PriceQuote lookupPrice(String itemId) {
        BazaarData.LookupResult bazaar = BazaarData.lookupProduct(itemId);
        if (bazaar.hasValue() && validPrice(bazaar.value().sell())) {
            return new PriceQuote(
                    bazaar.value().sell(),
                    true,
                    bazaar.stale(),
                    bazaar.loading(),
                    bazaar.refreshFailed()
            );
        }

        LowestBinData.LookupResult auction = LowestBinData.lookupLowestBin(itemId);
        Optional<AuctionReferencePrice> reference = AuctionReferencePrice.select(
                auction.value(),
                auction.average7d(),
                auction.average30d()
        );
        if (reference.isPresent()) {
            AuctionReferencePrice selected = reference.orElseThrow();
            return new PriceQuote(
                    selected.value(),
                    selected.safeForProtection(),
                    auction.stale(),
                    bazaar.loading() || auction.loading(),
                    bazaar.refreshFailed() && auction.refreshFailed()
            );
        }
        return new PriceQuote(
                null,
                false,
                bazaar.stale() || auction.stale(),
                bazaar.loading() || auction.loading(),
                bazaar.refreshFailed() || auction.refreshFailed()
        );
    }

    private static boolean validPrice(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    static HudContent content(View current, Evaluation evaluation) {
        if (!current.visible()) {
            return hiddenContent();
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("own_value", Component.translatable(
                "marketguard.hud.trade_guard.own_value",
                CoinFormat.coins(evaluation.own().total())
        ).withStyle(ChatFormatting.GOLD));
        lines.put("partner_value", Component.translatable(
                "marketguard.hud.trade_guard.partner_value",
                CoinFormat.coins(evaluation.partner().total())
        ).withStyle(ChatFormatting.GOLD));

        ChatFormatting differenceColor = evaluation.difference() > 0.0
                ? ChatFormatting.GREEN
                : evaluation.difference() < 0.0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
        lines.put("difference", Component.translatable(
                evaluation.difference() >= 0.0
                        ? "marketguard.hud.trade_guard.difference_gain"
                        : "marketguard.hud.trade_guard.difference_loss",
                CoinFormat.coins(Math.abs(evaluation.difference()))
        ).withStyle(differenceColor));

        int ownUnpriced = evaluation.own().unpricedStacks();
        int partnerUnpriced = evaluation.partner().unpricedStacks();
        if (ownUnpriced > 0 || partnerUnpriced > 0) {
            lines.put("unpriced", Component.translatable(
                    "marketguard.hud.trade_guard.unpriced",
                    ownUnpriced,
                    partnerUnpriced
            ).withStyle(ChatFormatting.YELLOW));
        }

        lines.put("data", dataStatus(evaluation));
        if (evaluation.warning()) {
            lines.put("warning", Component.translatable(
                    "marketguard.hud.trade_guard.warning",
                    CoinFormat.coins(-evaluation.difference()),
                    percentage(evaluation.disadvantagePercentage())
            ).withStyle(ChatFormatting.RED));
        }
        return build(lines);
    }

    private static Component dataStatus(Evaluation evaluation) {
        SideValue own = evaluation.own();
        SideValue partner = evaluation.partner();
        String key;
        ChatFormatting color;
        if (own.unpricedStacks() > 0 || partner.unpricedStacks() > 0) {
            if (own.loading() || partner.loading()) {
                key = "marketguard.hud.trade_guard.data.loading";
            } else if (own.refreshFailed() || partner.refreshFailed()) {
                key = "marketguard.hud.trade_guard.data.unavailable";
            } else {
                key = "marketguard.hud.trade_guard.data.partial";
            }
            color = ChatFormatting.YELLOW;
        } else if (own.lowConfidenceStacks() > 0 || partner.lowConfidenceStacks() > 0) {
            key = "marketguard.hud.trade_guard.data.low_confidence";
            color = ChatFormatting.YELLOW;
        } else if (own.stale() || partner.stale()) {
            key = "marketguard.hud.trade_guard.data.stale";
            color = ChatFormatting.YELLOW;
        } else {
            key = "marketguard.hud.trade_guard.data.current";
            color = ChatFormatting.GRAY;
        }
        return Component.translatable(key).withStyle(color);
    }

    private static HudContent build(Map<String, Component> lines) {
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.TRADE_GUARD)) {
            Component line = lines.get(id);
            if (line != null) {
                content.line(line);
                added = true;
            }
        }
        return added ? content.build() : hiddenContent();
    }

    private static HudContent hiddenContent() {
        return HudContent.builder().line(Component.literal("Trade Guard")).visible(false).build();
    }

    private static String percentage(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0);
    }

    record OfferItem(String itemId, int count) {}

    record Offer(List<OfferItem> items, int unidentifiedStacks) {
        static Offer empty() {
            return new Offer(List.of(), 0);
        }

        boolean hasItems() {
            return unidentifiedStacks > 0 || !items.isEmpty();
        }
    }

    record View(Offer own, Offer partner) {
        static View hidden() {
            return new View(null, null);
        }

        boolean visible() {
            return own != null && partner != null;
        }

        boolean hasItems() {
            return visible() && (own.hasItems() || partner.hasItems());
        }
    }

    record PriceQuote(
            Double value,
            boolean reliable,
            boolean stale,
            boolean loading,
            boolean refreshFailed
    ) {
        boolean hasValue() {
            return value != null && validPrice(value);
        }
    }

    record SideValue(
            double total,
            int pricedStacks,
            int unpricedStacks,
            int lowConfidenceStacks,
            boolean stale,
            boolean loading,
            boolean refreshFailed
    ) {
        boolean reliableForWarning() {
            return unpricedStacks == 0
                    && lowConfidenceStacks == 0
                    && !stale
                    && !loading
                    && !refreshFailed;
        }
    }

    record Evaluation(
            SideValue own,
            SideValue partner,
            double difference,
            boolean warning,
            double disadvantagePercentage
    ) {}

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "trade_guard")
        public static HudContent tradeGuard() {
            refreshPricesIfDue();
            View current = view;
            if (!current.visible() || !HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.TRADE_GUARD)) {
                return hiddenContent();
            }
            Evaluation evaluation = evaluate(
                    current.own(),
                    current.partner(),
                    TradeGuardHud::lookupPrice,
                    MarketGuardConfig.getUnderbiddingThreshold(),
                    MarketGuardConfig.getAbsoluteThreshold()
            );
            return content(current, evaluation);
        }
    }
}
