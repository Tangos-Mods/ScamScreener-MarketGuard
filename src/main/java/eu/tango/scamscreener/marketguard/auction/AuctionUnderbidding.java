package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.underbidding;

public final class AuctionUnderbidding {
    private static int threshold = 80;

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
        if (context.getMc().player == null) return;
        if (!isEnabled()) return;

        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context);
        if (pricing == null) return;

        double minimumAllowedPrice = pricing.lowestBin() * getMinimumAllowedPercentage();
        if (pricing.playerPrice() < minimumAllowedPrice) {
            double underbidPercent = ((pricing.lowestBin() - pricing.playerPrice()) / pricing.lowestBin()) * 100.0;
            context.cancel();
            context.bypass(4);
            underbidding(pricing.itemId(), underbidPercent, context.getRemainingBypassClicks(), context.getMc().player);
        }
    }

}
