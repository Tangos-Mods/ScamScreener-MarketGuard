package eu.tango.scamscreener.marketguard.profittracker;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.hud.MinionProfitHud;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfitTracker {
    private static final Object LOCK = new Object();
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern COIN_PATTERN = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)x\\s+(.+)");
    private static final Pattern BAZAAR_BUY = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?bought\\s+([0-9,]+)x\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern BAZAAR_SELL = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?sold\\s+([0-9,]+)x\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern BAZAAR_CLAIMED_BUY = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?(?:bazaar!\\s*)?claimed\\s+([0-9,]+)x\\s+(.+?)\\s+worth\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins\\s+bought\\s+for\\s+[0-9,]+(?:\\.[0-9]+)?\\s+each!?$");
    private static final Pattern BAZAAR_CLAIMED_SALE = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?(?:bazaar!\\s*)?claimed\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins\\s+from\\s+selling\\s+([0-9,]+)x\\s+(.+?)\\s+at\\s+[0-9,]+(?:\\.[0-9]+)?\\s+each!?$");
    private static final Pattern BAZAAR_BUY_ORDER_SETUP = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?buy\\s+order\\s+setup!\\s*([0-9,]+)x\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins\\.?$");
    private static final Pattern BAZAAR_BUY_ORDER_CANCELLED = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?cancelled!\\s*refunded\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins\\s+from\\s+cancelling\\s+buy\\s+order!?$");
    private static final Pattern BAZAAR_ORDER_FILLED = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?your\\s+(buy order|sell offer|sell order)\\s+for\\s+(.+?)\\s+was\\s+(?:(?:completely|fully)\\s+)?(?:filled|fulfilled).*$");
    private static final Pattern BAZAAR_ORDER_SLOT = Pattern.compile("(?i)^(buy|sell)\\s+(.+)$");
    private static final Pattern OFFER_AMOUNT = Pattern.compile("(?i)offer amount:\\s*([0-9.,]+(?:[kmb])?)x?");
    private static final Pattern FILLED_AMOUNT = Pattern.compile("(?i)filled:\\s*([0-9.,]+(?:[kmb])?)\\s*/");
    private static final Pattern PRICE_PER_UNIT = Pattern.compile("(?i)price per unit:\\s*([0-9,]+(?:\\.[0-9]+)?)\\s+coins");
    private static final Pattern AUCTION_PURCHASED = Pattern.compile("(?i)(?:\\[auction]\\s*)?you purchased\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern AUCTION_SOLD = Pattern.compile("(?i)(?:\\[auction]\\s*)?.*bought your\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern AUCTION_COLLECTED_SALE = Pattern.compile("(?i)(?:\\[auction]\\s*)?you collected\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins\\s+from\\s+selling\\s+(.+?)(?:\\s+to\\s+.+?)?\\s+in\\s+an\\s+auction!?$");
    private static final Pattern MINION_PAYOUT = Pattern.compile("(?i)^you received\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!$");
    private static final Pattern PERSONAL_BANK_INTEREST = Pattern.compile("(?i)^since you've been away you earned\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins as interest in your personal bank account!$");
    private static final Pattern COOP_BANK_INTEREST = Pattern.compile("(?i)^you have just received\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins as interest in your co-op bank account!$");
    private static final Pattern ALLOWANCE = Pattern.compile("(?i)^allowance!\\s+you earned\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!$");
    private static final String SKYBLOCK_WELCOME_MESSAGE = "Welcome to Hypixel SkyBlock!";
    private static final long CANDIDATE_MAX_AGE_MS = 30_000L;

    private static boolean initialized;
    private static boolean skyBlockSessionActive;
    private static Path storePath = ProfitTrackerStore.defaultPath();
    private static ProfitTrackerState state = new ProfitTrackerState();
    private static final Map<String, BazaarClickCandidate> pendingBazaarCandidates = new HashMap<>();
    private static final Map<String, AuctionPurchaseCandidate> pendingAuctionPurchases = new HashMap<>();
    private static final Map<String, MinionPayoutCandidate> pendingMinionPayouts = new HashMap<>();

    private ProfitTracker() {}

    public static void initialize() {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            initialized = true;
            state = ProfitTrackerStore.load(storePath);
        }

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleGameMessage(message.getString(), overlay));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEphemeralState());
    }

    public static double getBazaarAllTimeProfit() {
        String profileId = ProfileResolver.resolveCurrentProfileId(Minecraft.getInstance());
        return profileId == null ? 0.0 : getBazaarAllTimeProfit(profileId);
    }

    public static double getAuctionHouseAllTimeProfit() {
        String profileId = ProfileResolver.resolveCurrentProfileId(Minecraft.getInstance());
        return profileId == null ? 0.0 : getAuctionHouseAllTimeProfit(profileId);
    }

    public static double getBazaarAllTimeProfit(String profileId) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            return profile == null ? 0.0 : profile.bazaarAllTimeProfit;
        }
    }

    public static double getAuctionHouseAllTimeProfit(String profileId) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            return profile == null ? 0.0 : profile.auctionHouseAllTimeProfit;
        }
    }

    public static double getMinionAllTimeProfit(String profileId) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            return profile == null ? 0.0 : profile.minionAllTimeProfit;
        }
    }

    public static double getInterestAllTimeProfit(String profileId) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            return profile == null ? 0.0 : profile.interestAllTimeProfit;
        }
    }

    public static double getAllowanceAllTimeProfit(String profileId) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            return profile == null ? 0.0 : profile.allowanceAllTimeProfit;
        }
    }

    public static boolean isSkyBlockSessionActive() {
        synchronized (LOCK) {
            return skyBlockSessionActive;
        }
    }

    static boolean resetAll() {
        synchronized (LOCK) {
            ProfitTrackerState emptyState = new ProfitTrackerState();
            if (!ProfitTrackerStore.save(storePath, emptyState)) {
                return false;
            }

            state = emptyState;
            pendingBazaarCandidates.clear();
            pendingAuctionPurchases.clear();
            pendingMinionPayouts.clear();
            return true;
        }
    }

    public static void onAuctionInteract(AuctionInteractEvent.Context context) {
        if (context == null || context.isCancelled()) {
            return;
        }

        String profileId = ProfileResolver.resolveCurrentProfileId(context.getMc());
        if (profileId == null) {
            MarketGuard.debug("Profit tracker skipped auction interaction because no SkyBlock profile was detected");
            return;
        }

        try {
            String itemId = context.getAuctionItemId();
            String itemName = SkyBlockItemUtil.getDisplayName(context.getAuctionItemStack());
            if (context.isBinView()) {
                rememberAuctionPurchase(profileId, itemId, itemName, context.getPlayerPrice());
            } else if (context.isCreateBinClick()) {
                rememberAuctionListing(profileId, itemId, itemName, context.getPlayerPrice());
            }
        } catch (Exception e) {
            MarketGuard.debug("Profit tracker skipped auction interaction error='{}'", e.getMessage());
        }
    }

    public static void onHandledScreenClick(
            Minecraft client,
            String title,
            AbstractContainerMenu screenHandler,
            Slot slot,
            int slotId,
            ContainerInput actionType
    ) {
        boolean minionScreen = MinionProfitHud.isMinionScreen(title);
        if ((!BazaarInventory.matchesAny(title) && !minionScreen) || client == null) {
            return;
        }
        if (actionType != ContainerInput.PICKUP || slot == null || slot.getItem().isEmpty()) {
            return;
        }

        String profileId = ProfileResolver.resolveCurrentProfileId(client);
        if (profileId == null) {
            MarketGuard.debug("Profit tracker skipped screen click because no SkyBlock profile was detected");
            return;
        }

        if (minionScreen) {
            if (slotId == 28) {
                rememberMinionPayout(profileId, MinionProfitHud.heldCoins(slot.getItem()));
            }
            return;
        }

        if (BazaarSlots.isClaimAllCoins(slotId, slot.getItem().getHoverName().getString())) {
            double claimedCoins = parseClaimedCoins(getLoreLines(slot.getItem()));
            if (claimedCoins <= 0.0) {
                return;
            }

            synchronized (LOCK) {
                if (claimBazaarCoins(state.getOrCreateProfile(profileId), claimedCoins)) {
                    MarketGuard.debug("Profit tracker recorded Claim All Coins profile='{}' claimedCoins={}", profileId, claimedCoins);
                } else {
                    MarketGuard.debug("Profit tracker found no filled Bazaar sell offers for Claim All Coins profile='{}'", profileId);
                }
                saveState();
            }
            return;
        }

        if (BazaarInventory.ORDERS.matches(title)) {
            BazaarOrderSlot orderSlot = parseBazaarOrderSlot(slot.getItem());
            if (orderSlot != null) {
                synchronized (LOCK) {
                    rememberBazaarOrderFromSlot(state.getOrCreateProfile(profileId), orderSlot);
                    saveState();
                }
                MarketGuard.debug(
                        "Profit tracker remembered Bazaar order slot profile='{}' kind={} item='{}' offerAmount={} filledAmount={}",
                        profileId,
                        orderSlot.kind,
                        orderSlot.itemName,
                        orderSlot.offerAmount,
                        orderSlot.filledAmount
                );
                return;
            }
        }

        BazaarClickCandidate candidate = parseBazaarClickCandidate(screenHandler, slot.getItem());
        if (candidate == null) {
            return;
        }

        synchronized (LOCK) {
            pendingBazaarCandidates.put(profileId, candidate);
        }
        MarketGuard.debug(
                "Remembered bazaar click candidate profile='{}' kind={} itemId='{}' quantity={} totalCoins={}",
                profileId,
                candidate.kind,
                candidate.itemId,
                candidate.quantity,
                candidate.totalCoins
        );
    }

    static void resetForTests() {
        synchronized (LOCK) {
            initialized = false;
            skyBlockSessionActive = false;
            state = new ProfitTrackerState();
            pendingBazaarCandidates.clear();
            pendingAuctionPurchases.clear();
            pendingMinionPayouts.clear();
            storePath = ProfitTrackerStore.defaultPath();
        }
        ProfileResolver.clearCurrentProfileId();
    }

    static void setStorePathForTests(Path path) {
        synchronized (LOCK) {
            storePath = path;
        }
    }

    static void setStateForTests(ProfitTrackerState replacement) {
        synchronized (LOCK) {
            state = replacement;
        }
    }

    static void rememberBazaarOrder(
            String profileId,
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity,
            double quotedTotalCoins
    ) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            profile.pendingBazaarOrders.add(new PendingBazaarOrder(
                    kind,
                    itemId,
                    itemName,
                    quantity,
                    quotedTotalCoins,
                    System.currentTimeMillis()
            ));
            saveState();
        }
    }

    static boolean confirmBazaarFill(String profileId, BazaarTradeKind kind, String itemId) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            if (profile == null) {
                return false;
            }

            PendingBazaarOrder match = findPendingBazaarOrder(profile.pendingBazaarOrders, kind, itemId, null);
            if (match == null) {
                return false;
            }

            if (kind.isBuy()) {
                if (!match.purchaseCostRecorded) {
                    recordBazaarTrade(profile, kind, match.itemId, match.itemName, match.quantity, match.quotedTotalCoins);
                    match.purchaseCostRecorded = true;
                }
                match.filled = true;
            } else {
                match.filled = true;
            }
            saveState();
            return true;
        }
    }

    static void recordBazaarInstantTrade(
            String profileId,
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity,
            double totalCoins
    ) {
        synchronized (LOCK) {
            recordBazaarTrade(state.getOrCreateProfile(profileId), kind, itemId, itemName, quantity, totalCoins);
            saveState();
        }
    }

    static boolean claimBazaarCoinsForTests(String profileId, double claimedCoins) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            if (profile == null || !claimBazaarCoins(profile, claimedCoins)) {
                return false;
            }
            saveState();
            return true;
        }
    }

    static void rememberMinionPayoutForTests(String profileId, double heldCoins) {
        synchronized (LOCK) {
            rememberMinionPayout(profileId, heldCoins);
        }
    }

    static boolean tryHandleMinionPayout(String profileId, String message) {
        Matcher matcher = MINION_PAYOUT.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        double receivedCoins = parsePrice(matcher.group(1));
        synchronized (LOCK) {
            MinionPayoutCandidate candidate = getPendingMinionPayout(profileId);
            if (candidate == null || Math.abs(candidate.heldCoins - receivedCoins) > 0.005) {
                return false;
            }
            state.getOrCreateProfile(profileId).minionAllTimeProfit += receivedCoins;
            pendingMinionPayouts.remove(profileId);
            saveState();
        }
        return true;
    }

    static void rememberAuctionListingForTests(String profileId, String itemId, String itemName, double salePrice) {
        synchronized (LOCK) {
            rememberAuctionListingLocked(profileId, itemId, itemName, salePrice);
            saveState();
        }
    }

    static boolean confirmAuctionSaleByItemId(String profileId, String itemId, double salePrice) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            if (profile == null) {
                return false;
            }

            PendingAuctionListing listing = findPendingAuctionListing(profile.pendingAuctionListings, itemId, null);
            if (listing != null) {
                profile.pendingAuctionListings.remove(listing);
            }
            boolean recorded = recordAuctionSale(profile, itemId, listing == null ? null : listing.itemName, salePrice);
            saveState();
            return recorded;
        }
    }

    static void rememberAuctionPurchaseForTests(String profileId, String itemId, String itemName, double paidPrice) {
        synchronized (LOCK) {
            pendingAuctionPurchases.put(profileId, new AuctionPurchaseCandidate(itemId, itemName, paidPrice, System.currentTimeMillis()));
        }
    }

    static boolean confirmAuctionPurchaseByItemId(String profileId, String itemId, double paidPrice) {
        synchronized (LOCK) {
            AuctionPurchaseCandidate candidate = getPendingAuctionPurchase(profileId);
            if (candidate == null || !candidate.matches(itemId, null)) {
                return false;
            }

            recordAuctionPurchase(state.getOrCreateProfile(profileId), candidate.itemId, candidate.itemName, paidPrice);
            pendingAuctionPurchases.remove(profileId);
            saveState();
            return true;
        }
    }

    private static void handleGameMessage(String rawMessage, boolean overlay) {
        if (overlay || rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        String message = normalizeWhitespace(rawMessage);
        tryStartSkyBlockSession(message);
        ProfileResolver.handleSystemMessage(rawMessage);
        String profileId = ProfileResolver.resolveCurrentProfileId(Minecraft.getInstance());
        if (profileId == null) {
            return;
        }

        if (tryHandleAuctionPurchase(profileId, message)
                || tryHandleAuctionSale(profileId, message)
                || tryHandleMinionPayout(profileId, message)
                || tryHandleInterest(profileId, message)
                || tryHandleAllowance(profileId, message)
                || tryHandleBazaarBuyOrderSetup(profileId, message)
                || tryHandleBazaarBuyOrderCancellation(profileId, message)
                || tryHandleBazaarInstantTrade(profileId, message, BazaarTradeKind.INSTANT_BUY)
                || tryHandleBazaarInstantTrade(profileId, message, BazaarTradeKind.INSTANT_SELL)
                || tryHandleBazaarClaimedBuy(profileId, message)
                || tryHandleBazaarClaimedSale(profileId, message)
                || tryHandleBazaarOrderFill(profileId, message)) {
            return;
        }
        tryHandleBazaarOrderCreationFromMessage(profileId, message);
    }

    private static boolean tryHandleAuctionPurchase(String profileId, String message) {
        Matcher matcher = AUCTION_PURCHASED.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        String itemName = matcher.group(1).trim();
        double paidPrice = parsePrice(matcher.group(2));
        synchronized (LOCK) {
            AuctionPurchaseCandidate candidate = getPendingAuctionPurchase(profileId);
            if (candidate != null && candidate.matches(null, itemName)) {
                recordAuctionPurchase(state.getOrCreateProfile(profileId), candidate.itemId, candidate.itemName, paidPrice);
                pendingAuctionPurchases.remove(profileId);
            } else {
                recordAuctionPurchase(state.getOrCreateProfile(profileId), null, itemName, paidPrice);
            }
            saveState();
        }
        return true;
    }

    static boolean tryHandleAuctionSale(String profileId, String message) {
        Matcher matcher = AUCTION_SOLD.matcher(message);
        String itemName;
        double salePrice;
        if (matcher.matches()) {
            itemName = matcher.group(1).trim();
            salePrice = parsePrice(matcher.group(2));
        } else {
            matcher = AUCTION_COLLECTED_SALE.matcher(message);
            if (!matcher.matches()) {
                return false;
            }
            salePrice = parsePrice(matcher.group(1));
            itemName = matcher.group(2).trim();
        }

        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            PendingAuctionListing listing = findPendingAuctionListing(profile.pendingAuctionListings, null, itemName);
            String itemId = listing == null ? null : listing.itemId;
            if (listing != null) {
                profile.pendingAuctionListings.remove(listing);
            }
            if (!recordAuctionSale(profile, itemId, itemName, salePrice)) {
                unmatched("auction sale", message);
            }
            saveState();
        }
        return true;
    }

    static boolean tryHandleInterest(String profileId, String message) {
        Matcher matcher = PERSONAL_BANK_INTEREST.matcher(message);
        if (!matcher.matches()) {
            matcher = COOP_BANK_INTEREST.matcher(message);
        }
        if (!matcher.matches()) {
            return false;
        }

        synchronized (LOCK) {
            state.getOrCreateProfile(profileId).interestAllTimeProfit += parsePrice(matcher.group(1));
            saveState();
        }
        return true;
    }

    static boolean tryHandleAllowance(String profileId, String message) {
        Matcher matcher = ALLOWANCE.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        synchronized (LOCK) {
            state.getOrCreateProfile(profileId).allowanceAllTimeProfit += parsePrice(matcher.group(1));
            saveState();
        }
        return true;
    }

    static boolean tryHandleBazaarInstantTrade(String profileId, String message, BazaarTradeKind kind) {
        Matcher matcher = (kind == BazaarTradeKind.INSTANT_BUY ? BAZAAR_BUY : BAZAAR_SELL).matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        int quantity = parseInt(matcher.group(1));
        String itemName = matcher.group(2).trim();
        double totalCoins = quantity * parsePrice(matcher.group(3));
        synchronized (LOCK) {
            BazaarClickCandidate candidate = getPendingBazaarCandidate(profileId);
            String itemId = candidate != null && candidate.kind == kind && candidate.matches(itemName)
                    ? candidate.itemId
                    : BazaarData.findItemIdByName(itemName);
            recordBazaarTrade(state.getOrCreateProfile(profileId), kind, itemId, itemName, quantity, totalCoins);
            if (candidate != null && candidate.kind == kind && candidate.matches(itemName)) {
                pendingBazaarCandidates.remove(profileId);
            }
            saveState();
        }
        return true;
    }

    static boolean tryHandleBazaarClaimedBuy(String profileId, String message) {
        Matcher matcher = BAZAAR_CLAIMED_BUY.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        int quantity = parseInt(matcher.group(1));
        String itemName = matcher.group(2).trim();
        double totalCoins = parsePrice(matcher.group(3));
        String itemId = BazaarData.findItemIdByName(itemName);
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            PendingBazaarOrder order = findPendingBazaarOrder(profile.pendingBazaarOrders, BazaarTradeKind.BUY_ORDER, itemId, itemName);
            boolean purchaseCostRecorded = false;
            if (order != null) {
                profile.pendingBazaarOrders.remove(order);
                itemId = order.itemId;
                purchaseCostRecorded = order.purchaseCostRecorded;
            }
            if (!purchaseCostRecorded) {
                recordBazaarTrade(profile, BazaarTradeKind.BUY_ORDER, itemId, itemName, quantity, totalCoins);
            }
            saveState();
        }
        return true;
    }

    static boolean tryHandleBazaarClaimedSale(String profileId, String message) {
        Matcher matcher = BAZAAR_CLAIMED_SALE.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        double totalCoins = parsePrice(matcher.group(1));
        int quantity = parseInt(matcher.group(2));
        String itemName = matcher.group(3).trim();
        String itemId = BazaarData.findItemIdByName(itemName);
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            PendingBazaarOrder order = findPendingBazaarOrder(profile.pendingBazaarOrders, BazaarTradeKind.SELL_ORDER, itemId, itemName);
            if (order != null) {
                profile.pendingBazaarOrders.remove(order);
                itemId = order.itemId;
            }
            recordBazaarTrade(profile, BazaarTradeKind.SELL_ORDER, itemId, itemName, quantity, totalCoins);
            saveState();
        }
        return true;
    }

    static boolean tryHandleBazaarBuyOrderSetup(String profileId, String message) {
        Matcher matcher = BAZAAR_BUY_ORDER_SETUP.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        int quantity = parseInt(matcher.group(1));
        String itemName = matcher.group(2).trim();
        double totalCoins = parsePrice(matcher.group(3));
        String itemId = BazaarData.findItemIdByName(itemName);
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            PendingBazaarOrder order = findPendingBazaarOrderByQuantity(
                    profile.pendingBazaarOrders,
                    BazaarTradeKind.BUY_ORDER,
                    itemId,
                    itemName,
                    quantity
            );
            if (order == null) {
                order = new PendingBazaarOrder(
                        BazaarTradeKind.BUY_ORDER,
                        itemId,
                        itemName,
                        quantity,
                        totalCoins,
                        System.currentTimeMillis()
                );
                profile.pendingBazaarOrders.add(order);
            } else {
                order.quotedTotalCoins = totalCoins;
            }
            if (!order.purchaseCostRecorded) {
                recordBazaarTrade(profile, BazaarTradeKind.BUY_ORDER, itemId, itemName, quantity, totalCoins);
                order.purchaseCostRecorded = true;
            }
            saveState();
        }
        return true;
    }

    static boolean tryHandleBazaarBuyOrderCancellation(String profileId, String message) {
        Matcher matcher = BAZAAR_BUY_ORDER_CANCELLED.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        double refundedCoins = parsePrice(matcher.group(1));
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            PendingBazaarOrder order = findPendingBuyOrderForRefund(profile, refundedCoins);
            if (order != null && order.refundRecorded) {
                return true;
            }

            profile.bazaarAllTimeProfit += refundedCoins;
            if (order != null) {
                order.refundRecorded = true;
            }
            saveState();
        }
        return true;
    }

    static boolean tryStartSkyBlockSession(String message) {
        if (!SKYBLOCK_WELCOME_MESSAGE.equals(normalizeWhitespace(message))) {
            return false;
        }

        synchronized (LOCK) {
            skyBlockSessionActive = true;
        }
        return true;
    }

    private static boolean tryHandleBazaarOrderCreationFromMessage(String profileId, String message) {
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        if ((!lowerMessage.contains("order") && !lowerMessage.contains("offer"))
                || (!lowerMessage.contains("created") && !lowerMessage.contains("setup") && !lowerMessage.contains("placed"))) {
            return false;
        }

        synchronized (LOCK) {
            BazaarClickCandidate candidate = getPendingBazaarCandidate(profileId);
            if (candidate == null || !candidate.kind.isOrder()) {
                return true;
            }

            rememberBazaarOrderLocked(profileId, candidate);
            pendingBazaarCandidates.remove(profileId);
            saveState();
        }
        return true;
    }

    static boolean tryHandleBazaarOrderFill(String profileId, String message) {
        Matcher matcher = BAZAAR_ORDER_FILLED.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        BazaarTradeKind kind = matcher.group(1).toLowerCase(Locale.ROOT).contains("buy")
                ? BazaarTradeKind.BUY_ORDER
                : BazaarTradeKind.SELL_ORDER;
        String itemName = withoutQuantityPrefix(matcher.group(2));
        String itemId = BazaarData.findItemIdByName(itemName);
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            if (profile == null) {
                unmatched("bazaar order fill", message);
                return true;
            }

            PendingBazaarOrder match = findPendingBazaarOrder(profile.pendingBazaarOrders, kind, itemId, itemName);
            if (match == null) {
                unmatched("bazaar order fill", message);
                return true;
            }

            match.filled = true;
            saveState();
        }
        return true;
    }

    private static BazaarClickCandidate parseBazaarClickCandidate(AbstractContainerMenu screenHandler, ItemStack clickedStack) {
        BazaarTradeKind kind = BazaarSlots.resolveKind(clickedStack.getHoverName().getString());
        if (kind == null || screenHandler == null || screenHandler.slots.size() <= BazaarSlots.ITEM_SLOT.slot()) {
            return null;
        }

        ItemStack itemStack = screenHandler.getSlot(BazaarSlots.ITEM_SLOT.slot()).getItem();
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        List<String> loreLines = getLoreLines(clickedStack);
        int quantity = parseQuantity(loreLines);
        if (quantity <= 0) {
            quantity = Math.max(1, itemStack.getCount());
        }

        double totalCoins = parseTotalCoins(loreLines, quantity);
        if (totalCoins <= 0.0) {
            return null;
        }

        return new BazaarClickCandidate(
                kind,
                SkyBlockItemUtil.getSkyblockId(itemStack),
                SkyBlockItemUtil.getDisplayName(itemStack),
                quantity,
                totalCoins,
                System.currentTimeMillis()
        );
    }

    private static BazaarOrderSlot parseBazaarOrderSlot(ItemStack stack) {
        return parseBazaarOrderSlot(stack.getHoverName().getString(), getLoreLines(stack));
    }

    static BazaarOrderSlot parseBazaarOrderSlot(String displayName, List<String> loreLines) {
        Matcher titleMatcher = BAZAAR_ORDER_SLOT.matcher(normalizeWhitespace(displayName));
        if (!titleMatcher.matches()) {
            return null;
        }

        BazaarTradeKind kind = titleMatcher.group(1).equalsIgnoreCase("buy")
                ? BazaarTradeKind.BUY_ORDER
                : BazaarTradeKind.SELL_ORDER;
        String itemName = titleMatcher.group(2).trim();
        int offerAmount = 0;
        int filledAmount = 0;
        double unitPrice = 0.0;
        for (String line : loreLines) {
            Matcher offerMatcher = OFFER_AMOUNT.matcher(line);
            if (offerMatcher.find()) {
                offerAmount = parseCompactQuantity(offerMatcher.group(1));
                continue;
            }
            Matcher filledMatcher = FILLED_AMOUNT.matcher(line);
            if (filledMatcher.find()) {
                filledAmount = parseCompactQuantity(filledMatcher.group(1));
                continue;
            }
            Matcher priceMatcher = PRICE_PER_UNIT.matcher(line);
            if (priceMatcher.find()) {
                unitPrice = parsePrice(priceMatcher.group(1));
            }
        }
        if (offerAmount <= 0 || unitPrice <= 0.0) {
            return null;
        }

        return new BazaarOrderSlot(
                kind,
                BazaarData.findItemIdByName(itemName),
                itemName,
                offerAmount,
                filledAmount,
                unitPrice
        );
    }

    private static void rememberBazaarOrderLocked(String profileId, BazaarClickCandidate candidate) {
        state.getOrCreateProfile(profileId).pendingBazaarOrders.add(new PendingBazaarOrder(
                candidate.kind,
                candidate.itemId,
                candidate.itemName,
                candidate.quantity,
                candidate.totalCoins,
                System.currentTimeMillis()
        ));
    }

    private static void rememberBazaarOrderFromSlot(ProfileProfitState profile, BazaarOrderSlot orderSlot) {
        PendingBazaarOrder order = findPendingBazaarOrderByQuantity(
                profile.pendingBazaarOrders,
                orderSlot.kind,
                orderSlot.itemId,
                orderSlot.itemName,
                orderSlot.offerAmount
        );
        if (order == null) {
            order = new PendingBazaarOrder(
                    orderSlot.kind,
                    orderSlot.itemId,
                    orderSlot.itemName,
                    orderSlot.offerAmount,
                    orderSlot.offerAmount * orderSlot.unitPrice,
                    System.currentTimeMillis()
            );
            profile.pendingBazaarOrders.add(order);
        }
        order.filled |= orderSlot.filledAmount >= orderSlot.offerAmount;
    }

    private static void rememberAuctionPurchase(String profileId, String itemId, String itemName, double paidPrice) {
        if (isMissing(itemId) && isMissing(itemName)) {
            return;
        }

        synchronized (LOCK) {
            pendingAuctionPurchases.put(profileId, new AuctionPurchaseCandidate(itemId, itemName, paidPrice, System.currentTimeMillis()));
        }
    }

    private static void rememberAuctionListing(String profileId, String itemId, String itemName, double salePrice) {
        if (isMissing(itemId) && isMissing(itemName)) {
            return;
        }

        synchronized (LOCK) {
            rememberAuctionListingLocked(profileId, itemId, itemName, salePrice);
            saveState();
        }
    }

    private static void rememberAuctionListingLocked(String profileId, String itemId, String itemName, double salePrice) {
        ProfileProfitState profile = state.getOrCreateProfile(profileId);
        profile.pendingAuctionListings.removeIf(listing ->
                matchesItem(listing.itemId, listing.itemName, itemId, itemName)
                        && Math.abs(listing.salePrice - salePrice) < 0.005
        );
        profile.pendingAuctionListings.add(new PendingAuctionListing(itemId, itemName, salePrice, System.currentTimeMillis()));
    }

    private static void recordBazaarTrade(
            ProfileProfitState profile,
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity,
            double totalCoins
    ) {
        if (quantity <= 0 || totalCoins < 0.0 || (isMissing(itemId) && isMissing(itemName))) {
            return;
        }

        if (kind.isBuy()) {
            profile.bazaarAllTimeProfit -= totalCoins;
            profile.trackedBazaarPositions.add(new TrackedBazaarPosition(
                    itemId,
                    itemName,
                    quantity,
                    totalCoins,
                    System.currentTimeMillis()
            ));
            return;
        }

        int remainingQuantity = quantity;
        for (Iterator<TrackedBazaarPosition> iterator = profile.trackedBazaarPositions.iterator(); iterator.hasNext() && remainingQuantity > 0; ) {
            TrackedBazaarPosition position = iterator.next();
            if (!matchesItem(position.itemId, position.itemName, itemId, itemName) || position.remainingQuantity <= 0) {
                continue;
            }

            int matchedQuantity = Math.min(remainingQuantity, position.remainingQuantity);
            double matchedCost = position.remainingCost * matchedQuantity / position.remainingQuantity;
            double matchedRevenue = totalCoins * matchedQuantity / quantity;
            profile.bazaarAllTimeProfit += matchedRevenue;
            position.remainingQuantity -= matchedQuantity;
            position.remainingCost -= matchedCost;
            remainingQuantity -= matchedQuantity;
            if (position.remainingQuantity == 0) {
                iterator.remove();
            }
        }
        if (remainingQuantity > 0) {
            profile.bazaarAllTimeProfit += totalCoins * remainingQuantity / quantity;
        }
    }

    private static boolean claimBazaarCoins(ProfileProfitState profile, double claimedCoins) {
        if (claimedCoins <= 0.0) {
            return false;
        }

        List<PendingBazaarOrder> filledSellOrders = new ArrayList<>();
        double quotedTotal = 0.0;
        for (PendingBazaarOrder order : profile.pendingBazaarOrders) {
            if (order.kind == BazaarTradeKind.SELL_ORDER && order.filled && order.quotedTotalCoins > 0.0) {
                filledSellOrders.add(order);
                quotedTotal += order.quotedTotalCoins;
            }
        }
        if (filledSellOrders.isEmpty()) {
            return false;
        }

        double assignedCoins = 0.0;
        for (int index = 0; index < filledSellOrders.size(); index++) {
            PendingBazaarOrder order = filledSellOrders.get(index);
            double saleCoins = index == filledSellOrders.size() - 1
                    ? claimedCoins - assignedCoins
                    : claimedCoins * order.quotedTotalCoins / quotedTotal;
            recordBazaarTrade(profile, BazaarTradeKind.SELL_ORDER, order.itemId, order.itemName, order.quantity, saleCoins);
            assignedCoins += saleCoins;
        }
        profile.pendingBazaarOrders.removeAll(filledSellOrders);
        return true;
    }

    private static void recordAuctionPurchase(ProfileProfitState profile, String itemId, String itemName, double paidPrice) {
        if (paidPrice < 0.0 || (isMissing(itemId) && isMissing(itemName))) {
            return;
        }
        profile.trackedAuctionPositions.add(new TrackedAuctionPosition(itemId, itemName, paidPrice, System.currentTimeMillis()));
    }

    private static boolean recordAuctionSale(ProfileProfitState profile, String itemId, String itemName, double salePrice) {
        if (salePrice < 0.0) {
            return false;
        }

        TrackedAuctionPosition position = findTrackedAuctionPosition(profile.trackedAuctionPositions, itemId, itemName);
        if (position == null) {
            return false;
        }

        profile.auctionHouseAllTimeProfit += salePrice - position.purchasePrice;
        profile.trackedAuctionPositions.remove(position);
        return true;
    }

    private static PendingBazaarOrder findPendingBazaarOrder(
            List<PendingBazaarOrder> orders,
            BazaarTradeKind kind,
            String itemId,
            String itemName
    ) {
        PendingBazaarOrder best = null;
        for (PendingBazaarOrder order : orders) {
            if (order.kind == kind && matchesItem(order.itemId, order.itemName, itemId, itemName)
                    && (best == null || order.createdAtMs < best.createdAtMs)) {
                best = order;
            }
        }
        return best;
    }

    private static PendingBazaarOrder findPendingBazaarOrderByQuantity(
            List<PendingBazaarOrder> orders,
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity
    ) {
        for (PendingBazaarOrder order : orders) {
            if (order.kind == kind
                    && order.quantity == quantity
                    && matchesItem(order.itemId, order.itemName, itemId, itemName)) {
                return order;
            }
        }
        return null;
    }

    private static PendingBazaarOrder findPendingBuyOrderForRefund(ProfileProfitState profile, double refundedCoins) {
        PendingBazaarOrder refundedOrder = null;
        PendingBazaarOrder unrefundedOrder = null;
        for (PendingBazaarOrder order : profile.pendingBazaarOrders) {
            if (order.kind != BazaarTradeKind.BUY_ORDER
                    || !order.purchaseCostRecorded
                    || order.quotedTotalCoins + 0.005 < refundedCoins) {
                continue;
            }
            if (order.refundRecorded) {
                if (refundedOrder == null || order.createdAtMs > refundedOrder.createdAtMs) {
                    refundedOrder = order;
                }
            } else if (unrefundedOrder == null || order.createdAtMs > unrefundedOrder.createdAtMs) {
                unrefundedOrder = order;
            }
        }
        return unrefundedOrder != null ? unrefundedOrder : refundedOrder;
    }

    private static PendingAuctionListing findPendingAuctionListing(
            List<PendingAuctionListing> listings,
            String itemId,
            String itemName
    ) {
        PendingAuctionListing best = null;
        for (PendingAuctionListing listing : listings) {
            if (matchesItem(listing.itemId, listing.itemName, itemId, itemName)
                    && (best == null || listing.createdAtMs < best.createdAtMs)) {
                best = listing;
            }
        }
        return best;
    }

    private static TrackedAuctionPosition findTrackedAuctionPosition(
            List<TrackedAuctionPosition> positions,
            String itemId,
            String itemName
    ) {
        TrackedAuctionPosition best = null;
        for (TrackedAuctionPosition position : positions) {
            if (matchesItem(position.itemId, position.itemName, itemId, itemName)
                    && (best == null || position.purchasedAtMs < best.purchasedAtMs)) {
                best = position;
            }
        }
        return best;
    }

    private static boolean matchesItem(String leftItemId, String leftItemName, String rightItemId, String rightItemName) {
        if (!isMissing(leftItemId) && !isMissing(rightItemId)) {
            return leftItemId.equals(rightItemId);
        }

        String leftName = normalizeName(leftItemName);
        String rightName = normalizeName(rightItemName);
        return !leftName.isEmpty() && leftName.equals(rightName);
    }

    private static int parseQuantity(List<String> loreLines) {
        for (String line : loreLines) {
            Matcher matcher = QUANTITY_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                return parseInt(matcher.group(1));
            }
            if (line.toLowerCase(Locale.ROOT).contains("amount")) {
                int value = parseFirstInt(line);
                if (value > 0) {
                    return value;
                }
            }
        }
        return 0;
    }

    private static double parseTotalCoins(List<String> loreLines, int quantity) {
        for (String line : loreLines) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (normalized.contains("coins")
                    && (normalized.contains("total") || normalized.contains("cost") || normalized.contains("offer"))
                    && !normalized.contains("per unit")) {
                double total = parseFirstPrice(line);
                if (total > 0.0) {
                    return total;
                }
            }
        }
        for (String line : loreLines) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (normalized.contains("coins") && normalized.contains("per unit")) {
                double unitPrice = parseFirstPrice(line);
                if (unitPrice > 0.0) {
                    return unitPrice * quantity;
                }
            }
        }
        return 0.0;
    }

    private static double parseClaimedCoins(List<String> loreLines) {
        for (String line : loreLines) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (normalized.contains("coins") && normalized.contains("to claim")) {
                return parseFirstPrice(line);
            }
        }
        return 0.0;
    }

    private static List<String> getLoreLines(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Component line : lore.lines()) {
            result.add(line.getString());
        }
        return result;
    }

    static void clearEphemeralState() {
        synchronized (LOCK) {
            pendingBazaarCandidates.clear();
            pendingAuctionPurchases.clear();
            pendingMinionPayouts.clear();
            skyBlockSessionActive = false;
        }
        ProfileResolver.clearCurrentProfileId();
    }

    private static void unmatched(String type, String message) {
        MarketGuard.debug("Profit tracker could not match {} confirmation message='{}'", type, message);
        if (!MarketGuardConfig.isWarnOnUnmatchedProfitConfirmations()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(MessageBuilder.PREFIX.copy().append(
                        Component.literal("Could not match a market confirmation for the profit tracker.").withStyle(ChatFormatting.YELLOW)
                ));
            }
        });
    }

    private static void saveState() {
        ProfitTrackerStore.save(storePath, state);
    }

    private static BazaarClickCandidate getPendingBazaarCandidate(String profileId) {
        BazaarClickCandidate candidate = pendingBazaarCandidates.get(profileId);
        if (candidate != null && System.currentTimeMillis() - candidate.createdAtMs > CANDIDATE_MAX_AGE_MS) {
            pendingBazaarCandidates.remove(profileId);
            return null;
        }
        return candidate;
    }

    private static AuctionPurchaseCandidate getPendingAuctionPurchase(String profileId) {
        AuctionPurchaseCandidate candidate = pendingAuctionPurchases.get(profileId);
        if (candidate != null && System.currentTimeMillis() - candidate.createdAtMs > CANDIDATE_MAX_AGE_MS) {
            pendingAuctionPurchases.remove(profileId);
            return null;
        }
        return candidate;
    }

    private static void rememberMinionPayout(String profileId, double heldCoins) {
        if (heldCoins <= 0.0) {
            return;
        }
        pendingMinionPayouts.put(profileId, new MinionPayoutCandidate(heldCoins, System.currentTimeMillis()));
    }

    private static MinionPayoutCandidate getPendingMinionPayout(String profileId) {
        MinionPayoutCandidate candidate = pendingMinionPayouts.get(profileId);
        if (candidate != null && System.currentTimeMillis() - candidate.createdAtMs > CANDIDATE_MAX_AGE_MS) {
            pendingMinionPayouts.remove(profileId);
            return null;
        }
        return candidate;
    }

    private static int parseFirstInt(String value) {
        Matcher matcher = COIN_PATTERN.matcher(value);
        return matcher.find() ? parseInt(matcher.group(1)) : 0;
    }

    private static double parseFirstPrice(String value) {
        Matcher matcher = COIN_PATTERN.matcher(value);
        return matcher.find() ? parsePrice(matcher.group(1)) : 0.0;
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private static int parseCompactQuantity(String value) {
        String normalized = value.replace(",", "").toLowerCase(Locale.ROOT).trim();
        double multiplier = 1.0;
        if (normalized.endsWith("k")) {
            multiplier = 1_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("m")) {
            multiplier = 1_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("b")) {
            multiplier = 1_000_000_000.0;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return (int)Math.round(Double.parseDouble(normalized) * multiplier);
    }

    private static double parsePrice(String value) {
        return Double.parseDouble(value.replace(",", ""));
    }

    private static String normalizeName(String value) {
        return normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private static String withoutQuantityPrefix(String value) {
        String normalized = normalizeWhitespace(value);
        Matcher matcher = QUANTITY_PATTERN.matcher(normalized);
        return matcher.matches() ? matcher.group(2).trim() : normalized;
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : WHITESPACE_PATTERN.matcher(value).replaceAll(" ").trim();
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private record BazaarClickCandidate(
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity,
            double totalCoins,
            long createdAtMs
    ) {
        boolean matches(String otherName) {
            return normalizeName(itemName).equals(normalizeName(otherName));
        }
    }

    record BazaarOrderSlot(
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int offerAmount,
            int filledAmount,
            double unitPrice
    ) {}

    private record AuctionPurchaseCandidate(String itemId, String itemName, double paidPrice, long createdAtMs) {
        boolean matches(String otherItemId, String otherItemName) {
            return matchesItem(itemId, itemName, otherItemId, otherItemName);
        }
    }

    private record MinionPayoutCandidate(double heldCoins, long createdAtMs) {}
}
