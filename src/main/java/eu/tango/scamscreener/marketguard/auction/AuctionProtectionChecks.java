package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;

final class AuctionProtectionChecks {
    private AuctionProtectionChecks() {}

    static boolean exceedsAbsoluteThreshold(double absoluteDifference) {
        return absoluteDifference >= MarketGuardConfig.getAbsoluteThreshold();
    }

    static void trigger(AuctionInteractEvent.Context context, String checkName, String percentLabel, String itemId, double percent) {
        context.cancel();
        context.bypass(4);
        MarketGuard.debug("{} triggered itemId='{}' {}={}", checkName, itemId, percentLabel, percent);
    }
}
