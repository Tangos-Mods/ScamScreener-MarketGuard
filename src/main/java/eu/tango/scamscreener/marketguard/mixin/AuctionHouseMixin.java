package eu.tango.scamscreener.marketguard.mixin;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionInventory;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.auction.LowestBIN;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(HandledScreen.class)
public abstract class AuctionHouseMixin {
    private static final AtomicInteger BYPASS_COUNTDOWN = new AtomicInteger();
    private static volatile String BYPASS_TITLE = null;
    private static volatile String pendingConfirmPurchaseItemId = null;
    private static volatile String lastSeenBinItemId = null;
    @Unique
    private String marketguard$lastDeferredBlacklistCheckKey = null;
    @Unique
    private boolean marketguard$loggedFilledPurchaseFlowSlots = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void prefetchLowestBinOnAuctionScreens(CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        if (!isAuctionScreen(title)) return;
        if (!isBinPurchaseFlowScreen(title)) {
            clearBinPurchaseFlowState();
        }

        LowestBIN.resetBlacklistNoticeState();
        MarketGuard.debug("Auction screen opened title='{}', requesting Lowest BIN refresh if needed", title);
        LowestBIN.refreshAsyncIfNeeded();
        debugPurchaseFlowSlots(screen, title);
        if (shouldTriggerBlacklistCheckOnOpen(title)) {
            triggerBlacklistCheck(screen, title);
        }
    }

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
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        ScreenHandler sh = mc.player != null ? mc.player.currentScreenHandler : null;
        AuctionInteractEvent.Context context = null;
        if (mc.player != null && mc.currentScreen != null && mc.currentScreen.getTitle() != null && sh != null) {
            context = new AuctionInteractEvent.Context(
                    mc,
                    screen,
                    sh,
                    slot,
                    slotId,
                    button,
                    actionType,
                    clicks -> scheduleBypass(currentTitle, clicks),
                    () -> BYPASS_COUNTDOWN.get()
            );
        }
        if (isAuctionScreen(currentTitle)) {
            MarketGuard.debug(
                    "HandledScreen click title='{}' slotId={} button={} actionType={} slotItem='{}' bypassRemaining={}",
                    currentTitle,
                    slotId,
                    button,
                    actionType,
                    slot.getStack().isEmpty() ? "<empty>" : slot.getStack().getName().getString(),
                    BYPASS_COUNTDOWN.get()
            );
            if (context != null && context.isBinView()) {
                rememberPendingConfirmPurchaseItemId(context.getAuctionItemId());
            }
        }
        resetBypassIfTitleChanged(currentTitle);
        if (consumeBypass()) return;

        if (context == null) return;
        String screenTitle = screen.getTitle() != null ? screen.getTitle().getString() : currentTitle;
        AuctionInteractEvent.EVENT.invoker().onInteract(context);

        if (context.isCancelled()) {
            MarketGuard.debug("Click cancelled for title='{}' slotId={}", screenTitle, slotId);
            ci.cancel();
        }
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void resetBypassOnScreenClose(CallbackInfo ci) {
        resetBypass();
        LowestBIN.resetBlacklistNoticeState();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void runDeferredAuctionBlacklistCheck(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        if (!isAuctionScreen(title)) {
            return;
        }

        if (isBinPurchaseFlowScreen(title) && !marketguard$loggedFilledPurchaseFlowSlots && hasAnyNonEmptyAuctionTopSlot(screen)) {
            marketguard$loggedFilledPurchaseFlowSlots = true;
            debugPurchaseFlowSlots(screen, title);
        }

        String itemId = resolveAuctionItemId(screen, title);
        if (itemId == null) {
            return;
        }

        String checkKey = title + "|" + itemId;
        if (checkKey.equals(marketguard$lastDeferredBlacklistCheckKey)) {
            return;
        }

        marketguard$lastDeferredBlacklistCheckKey = checkKey;
        MarketGuard.debug("Deferred auction blacklist check requested title='{}' itemId='{}'", title, itemId);
        LowestBIN.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
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
            MarketGuard.debug("Scheduled bypass title='{}' clicks={} countdown={}", title, clicks, countdown);
        }
    }

    private static boolean consumeBypass() {
        while (true) {
            int current = BYPASS_COUNTDOWN.get();
            if (current <= 0) return false;
            int next = current - 1;
            if (BYPASS_COUNTDOWN.compareAndSet(current, next)) {
                if (BYPASS_TITLE != null) {
                    MarketGuard.debug("Consuming bypass title='{}' current={} next={}", BYPASS_TITLE, current, next);
                }
                return next == 0;
            }
        }
    }

    private static void resetBypassIfTitleChanged(String currentTitle) {
        String bypassTitle = BYPASS_TITLE;
        if (bypassTitle == null) return;
        if (currentTitle == null || !bypassTitle.equals(currentTitle)) {
            MarketGuard.debug("Resetting bypass because title changed from '{}' to '{}'", bypassTitle, currentTitle);
            resetBypass();
        }
    }

    private static void resetBypass() {
        if (BYPASS_TITLE != null || BYPASS_COUNTDOWN.get() > 0) {
            MarketGuard.debug("Resetting bypass state title='{}' countdown={}", BYPASS_TITLE, BYPASS_COUNTDOWN.get());
        }
        BYPASS_TITLE = null;
        BYPASS_COUNTDOWN.set(0);
    }

    private static boolean isAuctionScreen(String title) {
        return AuctionInventory.matchesAny(title);
    }

    private static void triggerBlacklistCheck(HandledScreen<?> screen, String title) {
        String itemId = resolveAuctionItemId(screen, title);
        if (itemId == null) {
            MarketGuard.debug("Auction blacklist check skipped title='{}' because no SkyBlock item id was available", title);
            return;
        }

        MarketGuard.debug("Auction blacklist check requested title='{}' itemId='{}'", title, itemId);
        LowestBIN.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
    }

    private static String resolveAuctionItemId(HandledScreen<?> screen, String title) {
        String itemId = resolveAuctionItemId(screen);
        if (itemId != null) {
            if (isBinPurchaseFlowScreen(title)) {
                rememberLastSeenBinItemId(itemId);
            }
            if (isConfirmPurchaseScreen(title)) {
                clearPendingConfirmPurchaseItemId();
            }
            return itemId;
        }

        if (!isConfirmPurchaseScreen(title)) {
            return null;
        }

        String pendingItemId = consumePendingConfirmPurchaseItemId();
        if (pendingItemId != null) {
            MarketGuard.debug("Using pending BIN item id for confirm purchase title='{}' itemId='{}'", title, pendingItemId);
            return pendingItemId;
        }

        if (lastSeenBinItemId != null) {
            MarketGuard.debug("Using last seen BIN item id for confirm purchase title='{}' itemId='{}'", title, lastSeenBinItemId);
        }
        return lastSeenBinItemId;
    }

    private static String resolveAuctionItemId(HandledScreen<?> screen) {
        if (screen == null || screen.getScreenHandler() == null) {
            return null;
        }

        int itemSlot = AuctionSlots.ITEM.getSlot();
        if (screen.getScreenHandler().slots.size() <= itemSlot) {
            return null;
        }

        ItemStack itemStack = screen.getScreenHandler().getSlot(itemSlot).getStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        return SkyBlockItemUtil.getSkyblockId(itemStack);
    }

    private static void debugPurchaseFlowSlots(HandledScreen<?> screen, String title) {
        if (!isBinPurchaseFlowScreen(title)) {
            return;
        }
        if (screen == null || screen.getScreenHandler() == null) {
            MarketGuard.debug("Auction slot dump skipped title='{}' because screen handler was missing", title);
            return;
        }

        int expectedItemSlot = AuctionSlots.ITEM.getSlot();
        MarketGuard.debug(
                "Auction slot dump title='{}' expectedBinViewItemSlot={} slotCount={}",
                title,
                expectedItemSlot,
                screen.getScreenHandler().slots.size()
        );

        if (title.contains(AuctionInventory.BIN_VIEW.getTitle())) {
            String itemId = readSlotItemId(screen, expectedItemSlot);
            String itemName = readSlotItemName(screen, expectedItemSlot);
            MarketGuard.debug(
                    "BIN Auction View configured item slot={} item='{}' skyblockId='{}'",
                    expectedItemSlot,
                    itemName,
                    itemId == null ? "<none>" : itemId
            );
        }

        int upperBound = Math.min(53, screen.getScreenHandler().slots.size() - 1);
        for (int slotIndex = 0; slotIndex <= upperBound; slotIndex++) {
            String marker = slotIndex == expectedItemSlot ? " expectedItemSlot" : "";
            String itemName = readSlotItemName(screen, slotIndex);
            String itemId = readSlotItemId(screen, slotIndex);
            MarketGuard.debug(
                    "Auction slot dump title='{}' slot={}{} item='{}' skyblockId='{}'",
                    title,
                    slotIndex,
                    marker,
                    itemName,
                    itemId == null ? "<none>" : itemId
            );
        }
    }

    private static boolean hasAnyNonEmptyAuctionTopSlot(HandledScreen<?> screen) {
        if (screen == null || screen.getScreenHandler() == null) {
            return false;
        }

        int upperBound = Math.min(26, screen.getScreenHandler().slots.size() - 1);
        for (int slotIndex = 0; slotIndex <= upperBound; slotIndex++) {
            ItemStack stack = screen.getScreenHandler().getSlot(slotIndex).getStack();
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String readSlotItemName(HandledScreen<?> screen, int slotIndex) {
        if (screen == null || screen.getScreenHandler() == null || screen.getScreenHandler().slots.size() <= slotIndex) {
            return "<missing>";
        }

        ItemStack stack = screen.getScreenHandler().getSlot(slotIndex).getStack();
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }

        return stack.getName().getString();
    }

    private static String readSlotItemId(HandledScreen<?> screen, int slotIndex) {
        if (screen == null || screen.getScreenHandler() == null || screen.getScreenHandler().slots.size() <= slotIndex) {
            return null;
        }

        ItemStack stack = screen.getScreenHandler().getSlot(slotIndex).getStack();
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        return SkyBlockItemUtil.getSkyblockId(stack);
    }

    private static boolean shouldTriggerBlacklistCheckOnOpen(String title) {
        return title != null;
    }

    private static boolean isBinPurchaseFlowScreen(String title) {
        return title != null
                && (AuctionInventory.BIN_VIEW.matches(title)
                || AuctionInventory.CONFIRM_PURCHASE.matches(title));
    }

    private static boolean isConfirmPurchaseScreen(String title) {
        return AuctionInventory.CONFIRM_PURCHASE.matches(title);
    }

    private static void rememberPendingConfirmPurchaseItemId(String itemId) {
        pendingConfirmPurchaseItemId = itemId;
        if (itemId != null) {
            MarketGuard.debug("Remembered BIN item id for confirm purchase fallback itemId='{}'", itemId);
        }
    }

    private static void rememberLastSeenBinItemId(String itemId) {
        lastSeenBinItemId = itemId;
    }

    private static String consumePendingConfirmPurchaseItemId() {
        String itemId = pendingConfirmPurchaseItemId;
        pendingConfirmPurchaseItemId = null;
        return itemId;
    }

    private static void clearPendingConfirmPurchaseItemId() {
        pendingConfirmPurchaseItemId = null;
    }

    private static void clearBinPurchaseFlowState() {
        pendingConfirmPurchaseItemId = null;
        lastSeenBinItemId = null;
    }

}
