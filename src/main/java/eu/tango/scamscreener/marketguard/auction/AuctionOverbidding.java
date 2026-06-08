package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.Minecraft;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.overbidding;

public final class AuctionOverbidding {
    public static final int DEFAULT_THRESHOLD = 120;

    private static int threshold = DEFAULT_THRESHOLD;

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

        String itemId = context.getAuctionItemId();
        if (itemId != null) {
            LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
        }

        Minecraft mc = context.getMc();
        if (mc == null || mc.player == null) return;
        if (!isEnabled()) return;

        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context, mc.player, false);
        if (pricing == null) return;

        double maximumAllowedPrice = pricing.lowestBin() * getMaximumAllowedPercentage();
        double absoluteDifference = pricing.playerPrice() - pricing.lowestBin();
        MarketGuard.debug(
                "Overbidding check itemId='{}' playerPrice={} lowestBin={} threshold={} maximumAllowedPrice={} absoluteDifference={} absoluteThreshold={}",
                pricing.itemId(),
                pricing.playerPrice(),
                pricing.lowestBin(),
                threshold,
                maximumAllowedPrice,
                absoluteDifference,
                eu.tango.scamscreener.marketguard.MarketGuardConfig.getAbsoluteThreshold()
        );
        if (pricing.playerPrice() > maximumAllowedPrice && AuctionProtectionChecks.exceedsAbsoluteThreshold(absoluteDifference)) {
            double overbidPercent = ((pricing.playerPrice() - pricing.lowestBin()) / pricing.lowestBin()) * 100.0;
            AuctionProtectionChecks.trigger(context, "Overbidding", "overbidPercent", pricing.itemId(), overbidPercent);
            overbidding(
                    pricing.itemId(),
                    pricing.displayName(),
                    overbidPercent,
                    maximumAllowedPrice,
                    context.getRemainingBypassClicks(),
                    mc.player
            );
            return;
        }

        MarketGuard.debug("Overbidding check passed itemId='{}'", pricing.itemId());
    }

    static boolean exceedsAbsoluteThreshold(double absoluteDifference) {
        return AuctionProtectionChecks.exceedsAbsoluteThreshold(absoluteDifference);
    }
}
