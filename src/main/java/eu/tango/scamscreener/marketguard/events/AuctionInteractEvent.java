package eu.tango.scamscreener.marketguard.events;

import eu.tango.scamscreener.marketguard.auction.AuctionInventory;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class AuctionInteractEvent {

    private AuctionInteractEvent() {}

    /**
     * Listener Interface
     *
     * All information that will be passed on later
     */
    @FunctionalInterface
    public interface Listener{
        void onInteract(Context context);
    }

    // Fabric Event Instance
    public static final Event<Listener> EVENT =
            EventFactory.createArrayBacked(
                    Listener.class,
                    listeners -> context -> {
                        for (Listener listener : listeners) {
                            listener.onInteract(context);
                        }
                    }
            );

    @Getter @RequiredArgsConstructor
    public static class Context {

        private final MinecraftClient mc;
        private final HandledScreen<?> screen;
        private final ScreenHandler screenHandler;
        private final Slot slot;
        private final int slotId;
        private final Integer button;
        private final SlotActionType actionType;

        private boolean cancelled = false;

        public void cancel() {
            this.cancelled = true;
        }

        public ItemStack getStack() {
            return slot.getStack();
        }

        public String getInventoryName() {
            return screen.getTitle().getString();
        }

        public ItemStack getAuctionItemStack() {
            int itemSlot = AuctionSlots.ITEM.getSlot();
            return screenHandler.getSlot(itemSlot).getStack();
        }

        public String getAuctionItemId() {
            return SkyBlockItemUtil.getSkyblockId(getAuctionItemStack());
        }

        public double getPlayerPrice() throws Exception {
            int priceSlot = AuctionSlots.ITEM_PRICE.getSlot();
            return SkyBlockItemUtil.getPriceFromNBT(screenHandler.getSlot(priceSlot).getStack());
        }

        public boolean isCreateBinClick() {
            if (getInventoryName() == null) return false;
            if (!getInventoryName().contains(AuctionInventory.CREATE_BIN.getTitle())) return false;
            return getSlotId() == AuctionSlots.CREATE_BIN.getSlot();
        }

    }

}

