package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.error;
import static eu.tango.scamscreener.marketguard.util.MessageBuilder.lowestBinUnavailable;

final class AuctionPricingResolver {
    private AuctionPricingResolver() {}

    record PricingData(String itemId, double lowestBin, double playerPrice) {}

    static PricingData resolve(AuctionInteractEvent.Context context) {
        ClientPlayerEntity player = context.getMc().player;
        if (player == null) return null;

        ItemStack itemStack = context.getAuctionItemStack();
        MarketGuard.debug(
                "Resolving pricing title='{}' clickedSlot={} actionType={} auctionItem='{}'",
                context.getInventoryName(),
                context.getSlotId(),
                context.getActionType(),
                itemStack.isEmpty() ? "<empty>" : itemStack.getName().getString()
        );
        if (itemStack.isEmpty()) {
            MarketGuard.debug("Pricing resolution aborted: auction item stack was empty");
            context.cancel();
            error(Text.literal("Could not find Auction Item").formatted(Formatting.RED), player);
            return null;
        }

        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            MarketGuard.debug("Pricing resolution aborted: no SkyBlock ID found for '{}'", itemStack.getName().getString());
            context.cancel();
            error(Text.literal("Could not read Skyblock ID for ").append(itemStack.getName()).formatted(Formatting.RED), player);
            return null;
        }
        MarketGuard.debug("Resolved SkyBlock item id='{}'", itemId);

        LowestBIN.LookupResult lookupResult = SkyBlockItemUtil.lookupLowestBin(itemId);
        if (!lookupResult.hasValue()) {
            if (lookupResult.loading()) {
                context.cancel();
                MarketGuard.debug("Pricing resolution blocked: Lowest BIN is still loading for '{}'", itemId);
                error(Text.literal("Lowest BIN prices are still loading. Please try again.").formatted(Formatting.YELLOW), player);
                return null;
            }

            if (lookupResult.refreshFailed()) {
                context.cancel();
                MarketGuard.debug("Pricing resolution blocked: Lowest BIN refresh failed and no cached value is available for '{}'", itemId);
                error(Text.literal("Lowest BIN prices could not be loaded. Please try again.").formatted(Formatting.RED), player);
                return null;
            }

            blockMissingLowestBin(context, player, itemId);
            return null;
        }

        if (lookupResult.stale()) {
            MarketGuard.debug("Pricing resolution continues with stale Lowest BIN value for '{}'", itemId);
            error(Text.literal("Lowest BIN prices could not be updated. MarketGuard is using stale values.").formatted(Formatting.YELLOW), player);
        }

        double lowestBin = lookupResult.value();
        if (lowestBin <= 0.0) {
            MarketGuard.debug("Pricing resolution aborted: Lowest BIN was invalid for '{}' value={}", itemId, lowestBin);
            context.cancel();
            error(Text.literal("Lowest BIN is invalid for ").append(itemId).formatted(Formatting.RED), player);
            return null;
        }
        MarketGuard.debug("Resolved Lowest BIN itemId='{}' value={}", itemId, lowestBin);

        try {
            double playerPrice = context.getPlayerPrice();
            MarketGuard.debug("Resolved player price itemId='{}' value={}", itemId, playerPrice);
            return new PricingData(itemId, lowestBin, playerPrice);
        } catch (Exception e) {
            MarketGuard.debug("Pricing resolution failed while reading player price for '{}' error='{}'", itemId, e.getMessage());
            context.cancel();
            error(Text.literal("Failed to catch item price: " + e.getMessage()).formatted(Formatting.RED), player);
            return null;
        }
    }

    static void blockMissingLowestBin(AuctionInteractEvent.Context context, @Nullable ClientPlayerEntity player, String itemId) {
        context.cancel();
        MarketGuard.debug("Pricing resolution blocked: no Lowest BIN value available for '{}'", itemId);
        context.bypass(4);
        int remainingClicks = context.getRemainingBypassClicks();
        MarketGuard.debug("Pricing resolution scheduled bypass for missing Lowest BIN itemId='{}' remainingClicks={}", itemId, remainingClicks);
        if (player != null) {
            lowestBinUnavailable(itemId, remainingClicks, player);
        }
    }
}
