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

@Mixin(HandledScreen.class)
public abstract class AuctionHouseMixin {
    @Inject(
            method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelClicksOnCustomInventories(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
        if (slot == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen == null || mc.currentScreen.getTitle() == null) return;
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh == null) return;

        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        AuctionInteractEvent.Context context = new AuctionInteractEvent.Context(
                mc, screen, sh, slot, slotId, button, actionType
        );
        AuctionInteractEvent.EVENT.invoker().onInteract(context);

        if (context.isCancelled()) ci.cancel();
    }

}
