package eu.tango.scamscreener.marketguard.mixin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseMixinTest {

    @AfterEach
    void clearPendingState() throws Exception {
        invoke("clearBinPurchaseFlowState");
    }

    @Test
    void purchaseScreensSkipOpenTimeBlacklistCheck() throws Exception {
        assertTrue((boolean) invoke("shouldTriggerBlacklistCheckOnOpen", "Auction Browser"));
        assertTrue((boolean) invoke("shouldTriggerBlacklistCheckOnOpen", "Bin Auction View"));
        assertTrue((boolean) invoke("shouldTriggerBlacklistCheckOnOpen", "Confirm Purchase"));

        invoke("rememberPendingConfirmPurchaseItemId", "FANCY_LEGGINGS");
        assertTrue((boolean) invoke("shouldTriggerBlacklistCheckOnOpen", "Confirm Purchase"));
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

    private static Object invoke(String methodName, Object... args) throws Exception {
        Method method;
        if ("shouldTriggerBlacklistCheckOnOpen".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, String.class);
        } else if ("rememberPendingConfirmPurchaseItemId".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, String.class);
        } else if ("rememberLastSeenBinItemId".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, String.class);
        } else if ("resolveAuctionItemId".equals(methodName)) {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName, net.minecraft.client.gui.screen.ingame.HandledScreen.class, String.class);
        } else {
            method = AuctionHouseMixin.class.getDeclaredMethod(methodName);
        }
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
