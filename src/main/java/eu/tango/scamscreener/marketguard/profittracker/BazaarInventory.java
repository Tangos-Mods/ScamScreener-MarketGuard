package eu.tango.scamscreener.marketguard.profittracker;

import lombok.Getter;

import java.util.Locale;

@Getter
enum BazaarInventory {
    MAIN("Bazaar"),
    INSTANT_BUY("Instant Buy"),
    INSTANT_SELL("Instant Sell"),
    BUY_ORDER("Buy Order"),
    SELL_OFFER("Sell Offer"),
    ORDERS("Bazaar Orders");

    private final String title;
    private final String normalizedTitle;

    BazaarInventory(String title) {
        this.title = title;
        this.normalizedTitle = normalize(title);
    }

    boolean matches(String screenTitle) {
        return normalize(screenTitle).contains(normalizedTitle);
    }

    static boolean matchesAny(String screenTitle) {
        String normalizedScreenTitle = normalize(screenTitle);
        if (normalizedScreenTitle.isEmpty()) {
            return false;
        }

        for (BazaarInventory inventory : values()) {
            if (normalizedScreenTitle.contains(inventory.normalizedTitle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.toLowerCase(Locale.ROOT);
    }
}
