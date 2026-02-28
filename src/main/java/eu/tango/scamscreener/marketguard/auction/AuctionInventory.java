package eu.tango.scamscreener.marketguard.auction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
}

