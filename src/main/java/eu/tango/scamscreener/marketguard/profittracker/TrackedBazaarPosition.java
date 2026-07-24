package eu.tango.scamscreener.marketguard.profittracker;

final class TrackedBazaarPosition {
    String itemId;
    String itemName;
    int remainingQuantity;
    double remainingCost;
    long acquiredAtMs;

    TrackedBazaarPosition() {}

    TrackedBazaarPosition(String itemId, String itemName, int remainingQuantity, double remainingCost, long acquiredAtMs) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.remainingQuantity = remainingQuantity;
        this.remainingCost = remainingCost;
        this.acquiredAtMs = acquiredAtMs;
    }
}
