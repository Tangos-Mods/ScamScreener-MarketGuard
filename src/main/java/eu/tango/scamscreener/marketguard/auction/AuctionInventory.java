package eu.tango.scamscreener.marketguard.auction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter @RequiredArgsConstructor
public enum AuctionInventory {

    MAIN("Auction House"),
    MAIN_COOP("Co-op Auction House"),
    CREATE_BIN("Create BIN Auction"),
    CONFIRM_BIN("Confirm BIN Auction"),
    BROWSER("Auction Browser"),
    BIN_VIEW("Bin Auction View"),
    CONFIRM_PURCHASE("Confirm Purchase");

    private final String title;

    public boolean matches(String screenTitle) {
        if (screenTitle == null || screenTitle.isBlank()) {
            return false;
        }

        return screenTitle.toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT));
    }

    public static boolean matchesAny(String screenTitle) {
        if (screenTitle == null || screenTitle.isBlank()) {
            return false;
        }

        for (AuctionInventory inventory : values()) {
            if (inventory.matches(screenTitle)) {
                return true;
            }
        }
        return false;
    }
}

