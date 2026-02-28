package eu.tango.scamscreener.marketguard.auction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter @RequiredArgsConstructor
public enum AuctionSlots {

    CREATE_BIN_CONFIRM("Confirm", 11),
    CREATE_BIN("Create BIN Auction", 29),
    ITEM_PRICE("Item price: (?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+) coins", 31),
    ITEM(null, 13),
    BUY_BIN_ITEM("Buy Item Right Now", 31);

    private final String itemName;
    private final int slot;

}
