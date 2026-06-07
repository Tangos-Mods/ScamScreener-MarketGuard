package eu.tango.scamscreener.marketguard.profittracker;

enum BazaarTradeKind {
    INSTANT_BUY,
    INSTANT_SELL,
    BUY_ORDER,
    SELL_ORDER;

    boolean isBuy() {
        return this == INSTANT_BUY || this == BUY_ORDER;
    }

    boolean isOrder() {
        return this == BUY_ORDER || this == SELL_ORDER;
    }
}
