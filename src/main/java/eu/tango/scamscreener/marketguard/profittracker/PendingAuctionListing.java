package eu.tango.scamscreener.marketguard.profittracker;

final class PendingAuctionListing {
    String itemId;
    String itemName;
    double salePrice;
    double frozenLowestBin;
    long createdAtMs;

    PendingAuctionListing() {}

    PendingAuctionListing(String itemId, String itemName, double salePrice, double frozenLowestBin, long createdAtMs) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.salePrice = salePrice;
        this.frozenLowestBin = frozenLowestBin;
        this.createdAtMs = createdAtMs;
    }
}
