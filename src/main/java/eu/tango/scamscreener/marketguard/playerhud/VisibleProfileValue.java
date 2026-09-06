package eu.tango.scamscreener.marketguard.playerhud;

import java.util.List;
import java.util.function.Function;

public final class VisibleProfileValue {
    private VisibleProfileValue() {}

    public record Item(String itemId, int count) {}

    public record ItemPrice(
            Double value,
            boolean lowQuality,
            boolean stale,
            boolean loading,
            boolean refreshFailed
    ) {
        public boolean usable() {
            return value != null && Double.isFinite(value) && value > 0.0;
        }
    }

    public record Estimate(
            double value,
            boolean hasValue,
            int includedBalances,
            int missingBalances,
            int pricedItems,
            int visibleItems,
            int missingItemPrices,
            int lowQualityItemPrices,
            boolean stale,
            boolean loading,
            boolean refreshFailed
    ) {}

    public static Estimate estimate(
            Double bank,
            Double purse,
            List<Item> items,
            Function<String, ItemPrice> priceLookup
    ) {
        double value = 0.0;
        int includedBalances = 0;
        if (validBalance(bank)) {
            value += bank;
            includedBalances++;
        }
        if (validBalance(purse)) {
            value += purse;
            includedBalances++;
        }
        int pricedItems = 0;
        int visibleItems = 0;
        int missingItemPrices = 0;
        int lowQualityItemPrices = 0;
        boolean stale = false;
        boolean loading = false;
        boolean refreshFailed = false;
        for (Item item : items) {
            if (item == null || item.count() <= 0) {
                continue;
            }

            visibleItems += item.count();
            ItemPrice price = item.itemId() == null || item.itemId().isBlank()
                    ? null
                    : priceLookup.apply(item.itemId());
            if (price == null) {
                missingItemPrices += item.count();
                continue;
            }

            stale |= price.stale();
            loading |= price.loading();
            refreshFailed |= price.refreshFailed();
            double stackValue = price.usable() ? price.value() * item.count() : Double.NaN;
            if (!Double.isFinite(stackValue)) {
                missingItemPrices += item.count();
                if (price.lowQuality()) {
                    lowQualityItemPrices += item.count();
                }
                continue;
            }

            value += stackValue;
            pricedItems += item.count();
        }

        return new Estimate(
                value,
                includedBalances > 0 || pricedItems > 0,
                includedBalances,
                2 - includedBalances,
                pricedItems,
                visibleItems,
                missingItemPrices,
                lowQualityItemPrices,
                stale,
                loading,
                refreshFailed
        );
    }

    private static boolean validBalance(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0;
    }
}
