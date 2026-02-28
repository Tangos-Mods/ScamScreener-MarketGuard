package eu.tango.scamscreener.marketguard.mixin;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(HandledScreen.class)
public abstract class AuctionHouseMixin {
    private static final AtomicInteger BYPASS_COUNTDOWN = new AtomicInteger();
    private static volatile String BYPASS_TITLE = null;

    @Inject(
            method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelClicksOnCustomInventories(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        String currentTitle = mc.currentScreen != null && mc.currentScreen.getTitle() != null
                ? mc.currentScreen.getTitle().getString()
                : null;
        resetBypassIfTitleChanged(currentTitle);
        if (consumeBypass()) return;

        if (mc.player == null) return;
        if (mc.currentScreen == null || mc.currentScreen.getTitle() == null) return;
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh == null) return;

        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        String screenTitle = screen.getTitle() != null ? screen.getTitle().getString() : null;
        AuctionInteractEvent.Context context = new AuctionInteractEvent.Context(
                mc,
                screen,
                sh,
                slot,
                slotId,
                button,
                actionType,
                clicks -> scheduleBypass(screenTitle, clicks),
                () -> BYPASS_COUNTDOWN.get()
        );
        AuctionInteractEvent.EVENT.invoker().onInteract(context);

        if (context.isCancelled()) ci.cancel();
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void resetBypassOnScreenClose(CallbackInfo ci) {
        resetBypass();
    }

    private static void scheduleBypass(String title, int clicks) {
        if (clicks <= 0) return;
        if (title == null || title.isBlank()) {
            resetBypass();
            return;
        }

        if (!title.equals(BYPASS_TITLE)) {
            BYPASS_TITLE = title;
            BYPASS_COUNTDOWN.set(0);
        }

        // `clicks` includes the current blocked click.
        // Example: bypass(4) => block current + next 2, then bypass on the 4th click.
        int countdown = Math.max(0, clicks - 1);
        if (BYPASS_COUNTDOWN.get() <= 0) {
            BYPASS_COUNTDOWN.set(countdown);
        }
    }

    private static boolean consumeBypass() {
        while (true) {
            int current = BYPASS_COUNTDOWN.get();
            if (current <= 0) return false;
            int next = current - 1;
            if (BYPASS_COUNTDOWN.compareAndSet(current, next)) {
                return next == 0;
            }
        }
    }

    private static void resetBypassIfTitleChanged(String currentTitle) {
        String bypassTitle = BYPASS_TITLE;
        if (bypassTitle == null) return;
        if (currentTitle == null || !bypassTitle.equals(currentTitle)) {
            resetBypass();
        }
    }

    private static void resetBypass() {
        BYPASS_TITLE = null;
        BYPASS_COUNTDOWN.set(0);
    }

}
