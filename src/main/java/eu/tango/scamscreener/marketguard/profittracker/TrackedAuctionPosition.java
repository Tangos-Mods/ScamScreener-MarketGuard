package eu.tango.scamscreener.marketguard.profittracker;

final class TrackedAuctionPosition {
    String itemId;
    String itemName;
    double purchasePrice;
    long purchasedAtMs;

    TrackedAuctionPosition() {}

    TrackedAuctionPosition(String itemId, String itemName, double purchasePrice, long purchasedAtMs) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.purchasePrice = purchasePrice;
        this.purchasedAtMs = purchasedAtMs;
    }
}
