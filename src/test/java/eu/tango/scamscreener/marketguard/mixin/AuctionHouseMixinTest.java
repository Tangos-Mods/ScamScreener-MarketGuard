package eu.tango.scamscreener.marketguard.mixin;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseMixinTest {

    @AfterEach
    void clearPendingState() throws Exception {
        invoke("clearBinPurchaseFlowState");
    }

    @Test
    void confirmPurchaseCanConsumePendingItemIdFallback() throws Exception {
        invoke("rememberPendingConfirmPurchaseItemId", "FANCY_LEGGINGS");

        assertEquals(
                "FANCY_LEGGINGS",
                invoke("resolveAuctionItemId", null, "Confirm Purchase")
        );
        assertNull(invoke("resolveAuctionItemId", null, "Confirm Purchase"));
    }

    @Test
    void confirmPurchaseCanUseLastSeenBinItemIdFallback() throws Exception {
        invoke("rememberLastSeenBinItemId", "FANCY_LEGGINGS");

        assertEquals(
                "FANCY_LEGGINGS",
                invoke("resolveAuctionItemId", null, "Confirm Purchase")
        );
    }

    @Test
    void hudSlotScrapingRunsOncePerTickInsteadOfPerFrame() {
        List<String> injectedMethods = Arrays.stream(AuctionHouseMixin.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Inject.class))
                .filter(Objects::nonNull)
                .flatMap(inject -> Arrays.stream(inject.method()))
                .toList();

        assertTrue(injectedMethods.contains("tick()V"));
        assertFalse(injectedMethods.contains("extractContents"));
    }

    @Test
    void itemSlotWorkIsSkippedWhileTheStackInstanceIsUnchanged() throws Exception {
        AuctionHouseMixin mixin = new AuctionHouseMixin() {};
        Method changed = AuctionHouseMixin.class.getDeclaredMethod("itemSlotStackChanged", ItemStack.class);
        changed.setAccessible(true);

        assertFalse((boolean) changed.invoke(mixin, (Object) null));
        assertTrue((boolean) changed.invoke(mixin, ItemStack.EMPTY));
        assertFalse((boolean) changed.invoke(mixin, ItemStack.EMPTY));
        assertTrue((boolean) changed.invoke(mixin, (Object) null));
    }

    private static Object invoke(String methodName, Object... args) throws Exception {
        Method method;
        if ("rememberPendingConfirmPurchaseItemId".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, String.class);
        } else if ("rememberLastSeenBinItemId".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, String.class);
        } else if ("resolveAuctionItemId".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class, String.class);
        } else {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName);
        }
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
