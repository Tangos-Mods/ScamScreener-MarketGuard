package eu.tango.scamscreener.marketguard.profittracker;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfitTracker {
    private static final Object LOCK = new Object();
    private static final Pattern COIN_PATTERN = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)x\\s+(.+)");
    private static final Pattern PROFILED_BAZAAR_BUY = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?bought\\s+([0-9,]+)x\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern PROFILED_BAZAAR_SELL = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?sold\\s+([0-9,]+)x\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern BAZAAR_ORDER_FILLED = Pattern.compile("(?i)(?:\\[bazaar]\\s*)?your\\s+(buy order|sell offer|sell order)\\s+for\\s+(.+?)\\s+was\\s+(?:filled|fulfilled).*$");
    private static final Pattern AUCTION_PURCHASED = Pattern.compile("(?i)(?:\\[auction]\\s*)?you purchased\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");
    private static final Pattern AUCTION_SOLD = Pattern.compile("(?i)(?:\\[auction]\\s*)?.*bought your\\s+(.+?)\\s+for\\s+([0-9,]+(?:\\.[0-9]+)?)\\s+coins!?$");

    private static boolean initialized;
    private static Path storePath = ProfitTrackerStore.defaultPath();
    private static ProfitTrackerState state = new ProfitTrackerState();
    private static final Map<String, BazaarClickCandidate> pendingBazaarCandidates = new HashMap<>();
    private static final Map<String, AuctionPurchaseCandidate> pendingAuctionPurchases = new HashMap<>();

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
        String profileId = ProfileResolver.resolveCurrentProfileId(MinecraftClient.getInstance());
        if (profileId == null) {
            return 0.0;
        }
        return getBazaarAllTimeProfit(profileId);
    }

    public static double getAuctionHouseAllTimeProfit() {
        String profileId = ProfileResolver.resolveCurrentProfileId(MinecraftClient.getInstance());
        if (profileId == null) {
            return 0.0;
        }
        return getAuctionHouseAllTimeProfit(profileId);
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
            if (context.isBinView()) {
                rememberAuctionPurchase(profileId, context.getAuctionItemId(), SkyBlockItemUtil.getDisplayName(context.getAuctionItemStack()), context.getPlayerPrice());
            } else if (context.isCreateBinClick()) {
                rememberAuctionListing(profileId, context.getAuctionItemId(), SkyBlockItemUtil.getDisplayName(context.getAuctionItemStack()), context.getPlayerPrice());
            }
        } catch (Exception e) {
            MarketGuard.debug("Profit tracker skipped auction interaction error='{}'", e.getMessage());
        }
    }

    public static void onHandledScreenInit(String title) {
        if (BazaarInventory.matchesAny(title)) {
            BazaarData.refreshAsyncIfNeeded();
        }
    }

    public static void onHandledScreenClick(
            MinecraftClient client,
            String title,
            ScreenHandler screenHandler,
            Slot slot,
            int slotId,
            SlotActionType actionType
    ) {
        if (!BazaarInventory.matchesAny(title) || client == null) {
            return;
        }
        if (actionType != SlotActionType.PICKUP || slot == null || slot.getStack().isEmpty()) {
            return;
        }

        String profileId = ProfileResolver.resolveCurrentProfileId(client);
        if (profileId == null) {
            MarketGuard.debug("Profit tracker skipped bazaar click because no SkyBlock profile was detected");
            return;
        }

        BazaarClickCandidate candidate = parseBazaarClickCandidate(screenHandler, slot.getStack());
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
            state = new ProfitTrackerState();
            pendingBazaarCandidates.clear();
            pendingAuctionPurchases.clear();
            storePath = ProfitTrackerStore.defaultPath();
        }
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
            double quotedTotalCoins,
            double frozenUnitPrice
    ) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            profile.pendingBazaarOrders.add(new PendingBazaarOrder(
                    kind,
                    itemId,
                    itemName,
                    quantity,
                    quotedTotalCoins,
                    frozenUnitPrice,
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

            applyBazaarProfit(profile, match.kind, match.quantity, match.quotedTotalCoins, match.frozenUnitPrice);
            profile.pendingBazaarOrders.remove(match);
            saveState();
            return true;
        }
    }

    static void recordBazaarInstantTrade(
            String profileId,
            BazaarTradeKind kind,
            int quantity,
            double totalCoins,
            double frozenUnitPrice
    ) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            applyBazaarProfit(profile, kind, quantity, totalCoins, frozenUnitPrice);
            saveState();
        }
    }

    static void rememberAuctionListingForTests(
            String profileId,
            String itemId,
            String itemName,
            double salePrice,
            double frozenLowestBin
    ) {
        synchronized (LOCK) {
            rememberAuctionListingLocked(profileId, itemId, itemName, salePrice, frozenLowestBin);
            saveState();
        }
    }

    static boolean confirmAuctionSaleByItemId(String profileId, String itemId, double salePrice) {
        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            if (profile == null) {
                return false;
            }

            PendingAuctionListing match = findPendingAuctionListing(profile.pendingAuctionListings, itemId, null);
            if (match == null) {
                return false;
            }

            applyAuctionSale(profile, match, salePrice);
            return true;
        }
    }

    static void rememberAuctionPurchaseForTests(
            String profileId,
            String itemId,
            String itemName,
            double paidPrice,
            double frozenLowestBin
    ) {
        synchronized (LOCK) {
            pendingAuctionPurchases.put(profileId, new AuctionPurchaseCandidate(itemId, itemName, paidPrice, frozenLowestBin, System.currentTimeMillis()));
        }
    }

    static boolean confirmAuctionPurchaseByItemId(String profileId, String itemId, double paidPrice) {
        synchronized (LOCK) {
            AuctionPurchaseCandidate candidate = pendingAuctionPurchases.get(profileId);
            if (candidate == null || !candidate.matches(itemId, null)) {
                return false;
            }

            applyAuctionPurchase(profileId, state.getOrCreateProfile(profileId), candidate, paidPrice);
            return true;
        }
    }

    private static void handleGameMessage(String rawMessage, boolean overlay) {
        if (overlay || rawMessage == null || rawMessage.isBlank()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String profileId = ProfileResolver.resolveCurrentProfileId(client);
        if (profileId == null) {
            return;
        }

        String message = rawMessage.replaceAll("\\s+", " ").trim();
        if (tryHandleAuctionPurchase(profileId, message)) {
            return;
        }
        if (tryHandleAuctionSale(profileId, message)) {
            return;
        }
        if (tryHandleBazaarInstantTrade(profileId, message, BazaarTradeKind.INSTANT_BUY)) {
            return;
        }
        if (tryHandleBazaarInstantTrade(profileId, message, BazaarTradeKind.INSTANT_SELL)) {
            return;
        }
        if (tryHandleBazaarOrderFill(profileId, message)) {
            return;
        }
        if (tryHandleBazaarOrderCreationFromMessage(profileId, message)) {
            return;
        }
    }

    private static boolean tryHandleAuctionPurchase(String profileId, String message) {
        Matcher matcher = AUCTION_PURCHASED.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        String itemName = matcher.group(1).trim();
        double paidPrice = parsePrice(matcher.group(2));
        synchronized (LOCK) {
            AuctionPurchaseCandidate candidate = pendingAuctionPurchases.get(profileId);
            if (candidate == null) {
                unmatched("auction purchase", message);
                return true;
            }
            if (!candidate.matches(null, itemName)) {
                unmatched("auction purchase", message);
                return true;
            }

            applyAuctionPurchase(profileId, state.getOrCreateProfile(profileId), candidate, paidPrice);
        }
        return true;
    }

    private static boolean tryHandleAuctionSale(String profileId, String message) {
        Matcher matcher = AUCTION_SOLD.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        String itemName = matcher.group(1).trim();
        double salePrice = parsePrice(matcher.group(2));
        String itemId = LowestBinData.findItemIdByName(itemName);

        synchronized (LOCK) {
            ProfileProfitState profile = state.getProfile(profileId);
            if (profile == null) {
                unmatched("auction sale", message);
                return true;
            }

            PendingAuctionListing match = findPendingAuctionListing(profile.pendingAuctionListings, itemId, itemName);
            if (match == null) {
                unmatched("auction sale", message);
                return true;
            }

            applyAuctionSale(profile, match, salePrice);
        }
        return true;
    }

    private static boolean tryHandleBazaarInstantTrade(String profileId, String message, BazaarTradeKind kind) {
        Matcher matcher = (kind == BazaarTradeKind.INSTANT_BUY ? PROFILED_BAZAAR_BUY : PROFILED_BAZAAR_SELL).matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        int quantity = parseInt(matcher.group(1));
        String itemName = matcher.group(2).trim();
        double totalCoins = parsePrice(matcher.group(3));

        BazaarClickCandidate candidate;
        synchronized (LOCK) {
            candidate = pendingBazaarCandidates.get(profileId);
            if (candidate != null && candidate.kind == kind && candidate.matches(itemName)) {
                ProfileProfitState profile = state.getOrCreateProfile(profileId);
                applyBazaarProfit(profile, kind, candidate.quantity, candidate.totalCoins, candidate.frozenUnitPrice);
                pendingBazaarCandidates.remove(profileId);
                saveState();
                return true;
            }
        }

        String itemId = BazaarData.findItemIdByName(itemName);
        if (itemId == null) {
            unmatched("bazaar instant trade", message);
            return true;
        }

        BazaarData.LookupResult lookup = BazaarData.lookupProduct(itemId);
        if (!lookup.hasValue()) {
            unmatched("bazaar instant trade", message);
            return true;
        }

        synchronized (LOCK) {
            ProfileProfitState profile = state.getOrCreateProfile(profileId);
            double frozenUnitPrice = kind.isBuy() ? lookup.value().buy() : lookup.value().sell();
            applyBazaarProfit(profile, kind, quantity, totalCoins, frozenUnitPrice);
            saveState();
        }
        return true;
    }

    private static boolean tryHandleBazaarOrderCreationFromMessage(String profileId, String message) {
        if (!message.toLowerCase(Locale.ROOT).contains("order") && !message.toLowerCase(Locale.ROOT).contains("offer")) {
            return false;
        }
        if (!message.toLowerCase(Locale.ROOT).contains("created")
                && !message.toLowerCase(Locale.ROOT).contains("setup")
                && !message.toLowerCase(Locale.ROOT).contains("placed")) {
            return false;
        }

        synchronized (LOCK) {
            BazaarClickCandidate candidate = pendingBazaarCandidates.get(profileId);
            if (candidate == null || !candidate.kind.isOrder()) {
                unmatched("bazaar order create", message);
                return true;
            }

            rememberBazaarOrder(
                    profileId,
                    candidate.kind,
                    candidate.itemId,
                    candidate.itemName,
                    candidate.quantity,
                    candidate.totalCoins,
                    candidate.frozenUnitPrice
            );
            pendingBazaarCandidates.remove(profileId);
        }
        return true;
    }

    private static boolean tryHandleBazaarOrderFill(String profileId, String message) {
        Matcher matcher = BAZAAR_ORDER_FILLED.matcher(message);
        if (!matcher.matches()) {
            return false;
        }

        BazaarTradeKind kind = matcher.group(1).toLowerCase(Locale.ROOT).contains("buy")
                ? BazaarTradeKind.BUY_ORDER
                : BazaarTradeKind.SELL_ORDER;
        String itemName = matcher.group(2).trim();
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

            applyBazaarProfit(profile, match.kind, match.quantity, match.quotedTotalCoins, match.frozenUnitPrice);
            profile.pendingBazaarOrders.remove(match);
            saveState();
        }
        return true;
    }

    private static BazaarClickCandidate parseBazaarClickCandidate(ScreenHandler screenHandler, ItemStack clickedStack) {
        BazaarTradeKind kind = BazaarSlots.resolveKind(clickedStack.getName().getString());
        if (kind == null || screenHandler == null || screenHandler.slots.size() <= BazaarSlots.ITEM_SLOT.slot()) {
            return null;
        }

        ItemStack itemStack = screenHandler.getSlot(BazaarSlots.ITEM_SLOT.slot()).getStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            return null;
        }

        BazaarData.LookupResult lookup = BazaarData.lookupProduct(itemId);
        if (!lookup.hasValue()) {
            return null;
        }

        List<String> loreLines = getLoreLines(clickedStack);
        int quantity = parseQuantity(loreLines);
        if (quantity <= 0) {
            quantity = itemStack.getCount() > 0 ? itemStack.getCount() : 1;
        }

        double totalCoins = parseTotalCoins(loreLines);
        if (totalCoins <= 0.0) {
            return null;
        }

        double frozenUnitPrice = kind.isBuy() ? lookup.value().buy() : lookup.value().sell();
        return new BazaarClickCandidate(
                kind,
                itemId,
                SkyBlockItemUtil.getDisplayName(itemStack),
                quantity,
                totalCoins,
                frozenUnitPrice,
                System.currentTimeMillis()
        );
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

    private static double parseTotalCoins(List<String> loreLines) {
        for (String line : loreLines) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (normalized.contains("coins")
                    && (normalized.contains("price")
                    || normalized.contains("cost")
                    || normalized.contains("total")
                    || normalized.contains("offer"))) {
                double price = parseFirstPrice(line);
                if (price > 0.0) {
                    return price;
                }
            }
        }
        return 0.0;
    }

    private static List<String> getLoreLines(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (Text line : lore.lines()) {
            result.add(line.getString());
        }
        return result;
    }

    private static void rememberAuctionPurchase(String profileId, String itemId, String itemName, double paidPrice) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }

        LowestBinData.LookupResult lookup = LowestBinData.lookupLowestBin(itemId);
        if (!lookup.hasValue()) {
            MarketGuard.debug("Profit tracker skipped auction purchase itemId='{}' because no cached Lowest BIN was available", itemId);
            return;
        }

        synchronized (LOCK) {
            pendingAuctionPurchases.put(
                    profileId,
                    new AuctionPurchaseCandidate(itemId, itemName, paidPrice, lookup.value(), System.currentTimeMillis())
            );
        }
        MarketGuard.debug("Remembered auction purchase candidate profile='{}' itemId='{}' paidPrice={} lowestBin={}", profileId, itemId, paidPrice, lookup.value());
    }

    private static void rememberAuctionListing(String profileId, String itemId, String itemName, double salePrice) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }

        LowestBinData.LookupResult lookup = LowestBinData.lookupLowestBin(itemId);
        if (!lookup.hasValue()) {
            MarketGuard.debug("Profit tracker skipped auction listing itemId='{}' because no cached Lowest BIN was available", itemId);
            return;
        }

        synchronized (LOCK) {
            rememberAuctionListingLocked(profileId, itemId, itemName, salePrice, lookup.value());
            saveState();
        }
        MarketGuard.debug("Remembered auction listing profile='{}' itemId='{}' salePrice={} lowestBin={}", profileId, itemId, salePrice, lookup.value());
    }

    private static void rememberAuctionListingLocked(String profileId, String itemId, String itemName, double salePrice, double frozenLowestBin) {
        ProfileProfitState profile = state.getOrCreateProfile(profileId);
        profile.pendingAuctionListings.removeIf(listing ->
                listing.itemId != null
                        && listing.itemId.equals(itemId)
                        && Math.abs(listing.salePrice - salePrice) < 0.005
        );
        profile.pendingAuctionListings.add(new PendingAuctionListing(itemId, itemName, salePrice, frozenLowestBin, System.currentTimeMillis()));
    }

    private static void applyAuctionSale(ProfileProfitState profile, PendingAuctionListing match, double salePrice) {
        profile.auctionHouseAllTimeProfit += salePrice - match.frozenLowestBin;
        profile.pendingAuctionListings.remove(match);
        saveState();
    }

    private static void applyAuctionPurchase(
            String profileId,
            ProfileProfitState profile,
            AuctionPurchaseCandidate candidate,
            double paidPrice
    ) {
        profile.auctionHouseAllTimeProfit += candidate.frozenLowestBin - paidPrice;
        pendingAuctionPurchases.remove(profileId);
        saveState();
    }

    private static PendingBazaarOrder findPendingBazaarOrder(
            List<PendingBazaarOrder> orders,
            BazaarTradeKind kind,
            String itemId,
            String itemName
    ) {
        PendingBazaarOrder best = null;
        for (PendingBazaarOrder order : orders) {
            if (order.kind != kind) {
                continue;
            }
            if (!matchesItem(order.itemId, order.itemName, itemId, itemName)) {
                continue;
            }
            if (best == null || order.createdAtMs < best.createdAtMs) {
                best = order;
            }
        }
        return best;
    }

    private static PendingAuctionListing findPendingAuctionListing(
            List<PendingAuctionListing> listings,
            String itemId,
            String itemName
    ) {
        PendingAuctionListing best = null;
        for (PendingAuctionListing listing : listings) {
            if (!matchesItem(listing.itemId, listing.itemName, itemId, itemName)) {
                continue;
            }
            if (best == null || listing.createdAtMs < best.createdAtMs) {
                best = listing;
            }
        }
        return best;
    }

    private static boolean matchesItem(String leftItemId, String leftItemName, String rightItemId, String rightItemName) {
        if (leftItemId != null && rightItemId != null && leftItemId.equals(rightItemId)) {
            return true;
        }

        return normalizeName(leftItemName).equals(normalizeName(rightItemName));
    }

    private static void applyBazaarProfit(
            ProfileProfitState profile,
            BazaarTradeKind kind,
            int quantity,
            double totalCoins,
            double frozenUnitPrice
    ) {
        double frozenTotal = frozenUnitPrice * Math.max(1, quantity);
        if (kind.isBuy()) {
            profile.bazaarAllTimeProfit += frozenTotal - totalCoins;
        } else {
            profile.bazaarAllTimeProfit += totalCoins - frozenTotal;
        }
    }

    private static void clearEphemeralState() {
        synchronized (LOCK) {
            pendingBazaarCandidates.clear();
            pendingAuctionPurchases.clear();
        }
    }

    private static void unmatched(String type, String message) {
        MarketGuard.debug("Profit tracker could not match {} confirmation message='{}'", type, message);

        if (!MarketGuardConfig.isWarnOnUnmatchedProfitConfirmations()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            if (client.player == null) {
                return;
            }

            client.player.sendMessage(
                    MessageBuilder.PREFIX.copy().append(
                            Text.literal("Could not match a market confirmation for the profit tracker.").formatted(Formatting.YELLOW)
                    ),
                    false
            );
        });
    }

    private static void saveState() {
        ProfitTrackerStore.save(storePath, state);
    }

    private static int parseFirstInt(String value) {
        Matcher matcher = COIN_PATTERN.matcher(value);
        if (!matcher.find()) {
            return 0;
        }
        return parseInt(matcher.group(1));
    }

    private static double parseFirstPrice(String value) {
        Matcher matcher = COIN_PATTERN.matcher(value);
        if (!matcher.find()) {
            return 0.0;
        }
        return parsePrice(matcher.group(1));
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private static double parsePrice(String value) {
        return Double.parseDouble(value.replace(",", ""));
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private record BazaarClickCandidate(
            BazaarTradeKind kind,
            String itemId,
            String itemName,
            int quantity,
            double totalCoins,
            double frozenUnitPrice,
            long createdAtMs
    ) {
        boolean matches(String otherName) {
            return normalizeName(itemName).equals(normalizeName(otherName));
        }
    }

    private record AuctionPurchaseCandidate(
            String itemId,
            String itemName,
            double paidPrice,
            double frozenLowestBin,
            long createdAtMs
    ) {
        boolean matches(String otherItemId, String otherItemName) {
            return matchesItem(itemId, itemName, otherItemId, otherItemName);
        }
    }
}
