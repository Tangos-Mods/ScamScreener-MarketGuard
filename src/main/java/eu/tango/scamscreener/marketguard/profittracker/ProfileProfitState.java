package eu.tango.scamscreener.marketguard.profittracker;

import java.util.ArrayList;
import java.util.List;

final class ProfileProfitState {
    double bazaarAllTimeProfit;
    double auctionHouseAllTimeProfit;
    double minionAllTimeProfit;
    double interestAllTimeProfit;
    double allowanceAllTimeProfit;
    List<PendingBazaarOrder> pendingBazaarOrders = new ArrayList<>();
    List<PendingAuctionListing> pendingAuctionListings = new ArrayList<>();
    List<TrackedBazaarPosition> trackedBazaarPositions = new ArrayList<>();
    List<TrackedAuctionPosition> trackedAuctionPositions = new ArrayList<>();
}
