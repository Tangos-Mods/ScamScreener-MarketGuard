package eu.tango.scamscreener.marketguard.profittracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
enum BazaarInventory {
    MAIN("Bazaar"),
    INSTANT_BUY("Instant Buy"),
    INSTANT_SELL("Instant Sell"),
    BUY_ORDER("Buy Order"),
    SELL_OFFER("Sell Offer");

    private final String title;

    boolean matches(String screenTitle) {
        if (screenTitle == null || screenTitle.isBlank()) {
            return false;
        }

        return screenTitle.toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT));
    }

    static boolean matchesAny(String screenTitle) {
        if (screenTitle == null || screenTitle.isBlank()) {
            return false;
        }

        for (BazaarInventory inventory : values()) {
            if (inventory.matches(screenTitle)) {
                return true;
            }
        }
        return false;
    }
}
