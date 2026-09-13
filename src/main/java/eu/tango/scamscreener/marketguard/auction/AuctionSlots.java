package eu.tango.scamscreener.marketguard.auction;

import lombok.Getter;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Pattern;

@Getter
public enum AuctionSlots {

    CREATE_BIN("Create BIN Auction", 29),
    ITEM_PRICE(null, 31),
    ITEM(null, 13),
    BUY_BIN_ITEM("Buy Item Right Now", 31);

    private final String itemName;
    private final int slot;
    private final Pattern itemPattern;

    AuctionSlots(String itemName, int slot) {
        this.itemName = itemName;
        this.slot = slot;
        this.itemPattern = itemName == null ? null : Pattern.compile(itemName);
    }

    public boolean matchesSlot(int slotId) {
        return slot == slotId;
    }

    public boolean matchesName(String stackName) {
        if (itemName == null || stackName == null || stackName.isBlank()) {
            return false;
        }

        return itemPattern.matcher(stackName).find();
    }

    public boolean matchesStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        return matchesName(stack.getHoverName().getString());
    }

}
