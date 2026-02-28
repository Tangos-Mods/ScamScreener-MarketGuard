package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.overbidding;

public final class AuctionOverbidding {
    public static final double percentage = 1.20;
    private AuctionOverbidding() {}

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isBinView()) return;
        if (context.getMc().player == null) return;

        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context);
        if (pricing == null) return;

        double maximumAllowedPrice = pricing.lowestBin() * percentage;
        if (pricing.playerPrice() > maximumAllowedPrice) {
            double overbidPercent = ((pricing.playerPrice() - pricing.lowestBin()) / pricing.lowestBin()) * 100.0;
            context.cancel();
            context.bypass(4);
            overbidding(pricing.itemId(), overbidPercent, context.getRemainingBypassClicks(), context.getMc().player);
        }
    }
}
