package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.underbidding;

public final class AuctionUnderbidding {
    public static final int DEFAULT_THRESHOLD = 80;

    private static int threshold = DEFAULT_THRESHOLD;

    private AuctionUnderbidding() {}

    public static int getThreshold() {
        return threshold;
    }

    public static void setThreshold(int threshold) {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("underbidding threshold must be between 0 and 100");
        }

        AuctionUnderbidding.threshold = threshold;
    }

    public static boolean isEnabled() {
        return threshold > 0 && threshold < 100;
    }

    public static double getMinimumAllowedPercentage() {
        return threshold / 100.0;
    }

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isCreateBinClick()) return;
        if (context.getMc() == null || context.getMc().player == null) return;
        if (!isEnabled()) return;

        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, context.getMc().player, true);
        if (pricing == null) return;

        double minimumAllowedPrice = pricing.lowestBin() * getMinimumAllowedPercentage();
        double absoluteDifference = pricing.lowestBin() - pricing.playerPrice();
        MarketGuard.debug(
                "Underbidding check itemId='{}' playerPrice={} lowestBin={} threshold={} minimumAllowedPrice={} absoluteDifference={} absoluteThreshold={}",
                pricing.itemId(),
                pricing.playerPrice(),
                pricing.lowestBin(),
                threshold,
                minimumAllowedPrice,
                absoluteDifference,
                eu.tango.scamscreener.marketguard.MarketGuardConfig.getAbsoluteThreshold()
        );
        if (pricing.playerPrice() < minimumAllowedPrice && AuctionProtectionChecks.exceedsAbsoluteThreshold(absoluteDifference)) {
            double underbidPercent = ((pricing.lowestBin() - pricing.playerPrice()) / pricing.lowestBin()) * 100.0;
            AuctionProtectionChecks.trigger(context, "Underbidding", "underbidPercent", pricing.itemId(), underbidPercent);
            underbidding(
                    pricing.itemId(),
                    pricing.displayName(),
                    underbidPercent,
                    minimumAllowedPrice,
                    context.getRemainingBypassClicks(),
                    context.getMc().player
            );
            return;
        }

        MarketGuard.debug("Underbidding check passed itemId='{}'", pricing.itemId());
    }

    static boolean exceedsAbsoluteThreshold(double absoluteDifference) {
        return AuctionProtectionChecks.exceedsAbsoluteThreshold(absoluteDifference);
    }

}
