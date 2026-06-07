package eu.tango.scamscreener.marketguard.profittracker;

final class PendingBazaarOrder {
    BazaarTradeKind kind;
    String itemId;
    String itemName;
    int quantity;
    double quotedTotalCoins;
    double frozenUnitPrice;
    long createdAtMs;

    PendingBazaarOrder() {}

    PendingBazaarOrder(
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity,
            double quotedTotalCoins,
            double frozenUnitPrice,
            long createdAtMs
    ) {
        this.kind = kind;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.quotedTotalCoins = quotedTotalCoins;
        this.frozenUnitPrice = frozenUnitPrice;
        this.createdAtMs = createdAtMs;
    }
}
