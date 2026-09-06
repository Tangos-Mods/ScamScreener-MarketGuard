package eu.tango.scamscreener.marketguard.mixin;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionInventory;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.hud.AuctionPriceHud;
import eu.tango.scamscreener.marketguard.hud.ForgeProfitHud;
import eu.tango.scamscreener.marketguard.hud.HudCustomization;
import eu.tango.scamscreener.marketguard.hud.MinionProfitHud;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;
import eu.tango.scamscreener.marketguard.hud.TradeGuardHud;
import eu.tango.scamscreener.marketguard.profittracker.ProfitTracker;
import eu.tango.scamscreener.marketguard.screen.HypixelScreens;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(AbstractContainerScreen.class)
public abstract class AuctionHouseMixin {
    private static final AtomicInteger BYPASS_COUNTDOWN = new AtomicInteger();
    private static volatile String BYPASS_TITLE = null;
    private static volatile String pendingConfirmPurchaseItemId = null;
    private static volatile String lastSeenBinItemId = null;
    @Unique
    private String marketguard$lastDeferredBlacklistCheckKey = null;
    @Unique
    private boolean marketguard$loggedFilledPurchaseFlowSlots = false;
    @Unique
    private boolean marketguard$playerHudShown = false;
    @Unique
    private String marketguard$auctionPriceWidgetKey = null;

    @Inject(method = "init", at = @At("TAIL"))
    private void prefetchLowestBinOnAuctionScreens(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        HudCustomization.setCurrentScreenTitle(title);
        showPlayerHudIfAvailable(screen, title);
        if (!isAuctionScreen(title)) {
            MarketGuard.debug("Container screen opened title='{}'", title);
            return;
        }
        if (!isBinPurchaseFlowScreen(title)) {
            clearBinPurchaseFlowState();
        }

        LowestBinData.resetBlacklistNoticeState();
        MarketGuard.debug("Auction screen opened title='{}', requesting Lowest BIN refresh if needed", title);
        LowestBinData.refreshAsyncIfNeeded();
        debugPurchaseFlowSlots(screen, title);
        if (shouldTriggerBlacklistCheckOnOpen(title)) {
            triggerBlacklistCheck(screen, title);
        }
    }

    @Inject(
            method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cancelClicksOnCustomInventories(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (slot == null) return;

        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        String currentTitle = screen.getTitle() != null ? screen.getTitle().getString() : null;
        AbstractContainerMenu sh = mc.player != null ? mc.player.containerMenu : null;
        AuctionInteractEvent.Context context = null;
        if (mc.player != null && currentTitle != null && sh != null) {
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
                    "AbstractContainerScreen click title='{}' slotId={} button={} actionType={} slotItem='{}' bypassRemaining={}",
                    currentTitle,
                    slotId,
                    button,
                    actionType,
                    slot.getItem().isEmpty() ? "<empty>" : slot.getItem().getHoverName().getString(),
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
            return;
        }

        ProfitTracker.onHandledScreenClick(mc, screenTitle, sh, slot, slotId, actionType);
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void resetBypassOnScreenClose(CallbackInfo ci) {
        HudCustomization.setCurrentScreenTitle(null);
        resetBypass();
        LowestBinData.resetBlacklistNoticeState();
        if (marketguard$playerHudShown) {
            PlayerHud.clear();
            marketguard$playerHudShown = false;
        }
        AuctionPriceHud.clear();
        marketguard$auctionPriceWidgetKey = null;
        TradeGuardHud.clear();
        MinionProfitHud.clear();
        ForgeProfitHud.clear();
    }

    @Inject(method = "extractContents", at = @At("HEAD"))
    private void runDeferredAuctionBlacklistCheck(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        String title = screen.getTitle() != null ? screen.getTitle().getString() : null;
        HudCustomization.setCurrentScreenTitle(title);
        showPlayerHudIfAvailable(screen, title);
        updateAuctionPriceWidget(screen, title);
        TradeGuardHud.update(screen.getMenu(), title);
        MinionProfitHud.update(screen.getMenu(), title);
        ForgeProfitHud.update(screen.getMenu(), title);
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
        LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
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

    private void showPlayerHudIfAvailable(AbstractContainerScreen<?> screen, String title) {
        if (marketguard$playerHudShown) {
            return;
        }

        String player = HypixelScreens.profilePlayer(title);
        String context = "profile";
        if (player == null && HypixelScreens.isTrade(title)) {
            player = HypixelScreens.tradePartner(title);
            context = "trade";
        }
        if (player == null && AuctionInventory.BIN_VIEW.matches(title)) {
            player = HypixelScreens.binSeller(screen.getMenu());
            context = "bin";
        }
        if (player == null) {
            return;
        }

        MarketGuard.debug("Player HUD opened context='{}' player='{}'", context, player);
        marketguard$playerHudShown = true;
        PlayerHud.show(player, null);
    }

    private void updateAuctionPriceWidget(AbstractContainerScreen<?> screen, String title) {
        if (!AuctionInventory.BIN_VIEW.matches(title) || screen.getMenu() == null) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
            return;
        }

        int itemSlot = AuctionSlots.ITEM.getSlot();
        if (screen.getMenu().slots.size() <= itemSlot) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
            return;
        }

        ItemStack auctionItem = screen.getMenu().getSlot(itemSlot).getItem();
        String itemId = SkyBlockItemUtil.getSkyblockId(auctionItem);
        if (itemId == null) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
            return;
        }

        String displayName = SkyBlockItemUtil.getDisplayName(auctionItem);
        String widgetKey = itemId + "|" + displayName;
        if (widgetKey.equals(marketguard$auctionPriceWidgetKey)) {
            return;
        }

        try {
            AuctionPriceHud.update(itemId, displayName, SkyBlockItemUtil.getPriceFromNBT(auctionItem));
            marketguard$auctionPriceWidgetKey = widgetKey;
        } catch (Exception ignored) {
            AuctionPriceHud.clear();
            marketguard$auctionPriceWidgetKey = null;
        }
    }

    private static void triggerBlacklistCheck(AbstractContainerScreen<?> screen, String title) {
        String itemId = resolveAuctionItemId(screen, title);
        if (itemId == null) {
            MarketGuard.debug("Auction blacklist check skipped title='{}' because no SkyBlock item id was available", title);
            return;
        }

        MarketGuard.debug("Auction blacklist check requested title='{}' itemId='{}'", title, itemId);
        LowestBinData.checkBlacklistedAuctioneerAsyncIfNeeded(itemId);
    }

    private static String resolveAuctionItemId(AbstractContainerScreen<?> screen, String title) {
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

    private static String resolveAuctionItemId(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) {
            return null;
        }

        int itemSlot = AuctionSlots.ITEM.getSlot();
        if (screen.getMenu().slots.size() <= itemSlot) {
            return null;
        }

        ItemStack itemStack = screen.getMenu().getSlot(itemSlot).getItem();
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        return SkyBlockItemUtil.getSkyblockId(itemStack);
    }

    private static void debugPurchaseFlowSlots(AbstractContainerScreen<?> screen, String title) {
        if (!isBinPurchaseFlowScreen(title)) {
            return;
        }
        if (screen == null || screen.getMenu() == null) {
            MarketGuard.debug("Auction slot dump skipped title='{}' because screen handler was missing", title);
            return;
        }

        int expectedItemSlot = AuctionSlots.ITEM.getSlot();
        MarketGuard.debug(
                "Auction slot dump title='{}' expectedBinViewItemSlot={} slotCount={}",
                title,
                expectedItemSlot,
                screen.getMenu().slots.size()
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

        int upperBound = Math.min(53, screen.getMenu().slots.size() - 1);
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

    private static boolean hasAnyNonEmptyAuctionTopSlot(AbstractContainerScreen<?> screen) {
        if (screen == null || screen.getMenu() == null) {
            return false;
        }

        int upperBound = Math.min(26, screen.getMenu().slots.size() - 1);
        for (int slotIndex = 0; slotIndex <= upperBound; slotIndex++) {
            ItemStack stack = screen.getMenu().getSlot(slotIndex).getItem();
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String readSlotItemName(AbstractContainerScreen<?> screen, int slotIndex) {
        if (screen == null || screen.getMenu() == null || screen.getMenu().slots.size() <= slotIndex) {
            return "<missing>";
        }

        ItemStack stack = screen.getMenu().getSlot(slotIndex).getItem();
        if (stack == null || stack.isEmpty()) {
            return "<empty>";
        }

        return stack.getHoverName().getString();
    }

    private static String readSlotItemId(AbstractContainerScreen<?> screen, int slotIndex) {
        if (screen == null || screen.getMenu() == null || screen.getMenu().slots.size() <= slotIndex) {
            return null;
        }

        ItemStack stack = screen.getMenu().getSlot(slotIndex).getItem();
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
