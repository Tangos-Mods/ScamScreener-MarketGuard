package eu.tango.scamscreener.marketguard.events;

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
    }

}

