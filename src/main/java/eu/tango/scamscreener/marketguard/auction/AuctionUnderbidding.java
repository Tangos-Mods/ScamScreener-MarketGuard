package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.underbidding;

public final class AuctionUnderbidding {
    private static final double percentage = 0.80;
    private AuctionUnderbidding() {}

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isCreateBinClick()) return;
        if (context.getMc().player == null) return;

        AuctionPricingResolver.PricingData pricing = AuctionPricingResolver.resolve(context);
        if (pricing == null) return;

        double minimumAllowedPrice = pricing.lowestBin() * percentage;
        if (pricing.playerPrice() < minimumAllowedPrice) {
            double underbidPercent = ((pricing.lowestBin() - pricing.playerPrice()) / pricing.lowestBin()) * 100.0;
            context.cancel();
            context.bypass(4);
            underbidding(pricing.itemId(), underbidPercent, context.getRemainingBypassClicks(), context.getMc().player);
        }
    }

}
