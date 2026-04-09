package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.overbidding;

public final class AuctionOverbidding {
    private static int threshold = 120;

    private AuctionOverbidding() {}

    public static int getThreshold() {
        return threshold;
    }

    public static void setThreshold(int threshold) {
        if (threshold < 100) {
            throw new IllegalArgumentException("overbidding threshold must be at least 100");
        }

        AuctionOverbidding.threshold = threshold;
    }

    public static boolean isEnabled() {
        return threshold > 100;
    }

    public static double getMaximumAllowedPercentage() {
        return threshold / 100.0;
    }

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isBinView()) return;
        if (context.getMc().player == null) return;
        if (!isEnabled()) return;

        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context);
        if (pricing == null) return;

        double maximumAllowedPrice = pricing.lowestBin() * getMaximumAllowedPercentage();
        MarketGuard.debug(
                "Overbidding check itemId='{}' playerPrice={} lowestBin={} threshold={} maximumAllowedPrice={}",
                pricing.itemId(),
                pricing.playerPrice(),
                pricing.lowestBin(),
                threshold,
                maximumAllowedPrice
        );
        if (pricing.playerPrice() > maximumAllowedPrice) {
            double overbidPercent = ((pricing.playerPrice() - pricing.lowestBin()) / pricing.lowestBin()) * 100.0;
            context.cancel();
            context.bypass(4);
            MarketGuard.debug("Overbidding triggered itemId='{}' overbidPercent={}", pricing.itemId(), overbidPercent);
            overbidding(pricing.itemId(), overbidPercent, context.getRemainingBypassClicks(), context.getMc().player);
            return;
        }

        MarketGuard.debug("Overbidding check passed itemId='{}'", pricing.itemId());
    }
}
