package eu.tango.scamscreener.marketguard.auction;

import lombok.Getter;

import java.util.Locale;

@Getter
public enum AuctionInventory {

    MAIN("Auction House"),
    MAIN_COOP("Co-op Auction House"),
    CREATE_BIN("Create BIN Auction"),
    CONFIRM_BIN("Confirm BIN Auction"),
    BROWSER("Auction Browser"),
    BIN_VIEW("Bin Auction View"),
    CONFIRM_PURCHASE("Confirm Purchase");

    private final String title;
    private final String normalizedTitle;

    AuctionInventory(String title) {
        this.title = title;
        this.normalizedTitle = normalize(title);
    }

    public boolean matches(String screenTitle) {
        return normalize(screenTitle).contains(normalizedTitle);
    }

    public static boolean matchesAny(String screenTitle) {
        String normalizedScreenTitle = normalize(screenTitle);
        if (normalizedScreenTitle.isEmpty()) {
            return false;
        }

        for (AuctionInventory inventory : values()) {
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

