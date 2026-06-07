package eu.tango.scamscreener.marketguard.profittracker;

import java.util.ArrayList;
import java.util.List;

final class ProfileProfitState {
    double bazaarAllTimeProfit;
    double auctionHouseAllTimeProfit;
    List<PendingBazaarOrder> pendingBazaarOrders = new ArrayList<>();
    List<PendingAuctionListing> pendingAuctionListings = new ArrayList<>();
}
