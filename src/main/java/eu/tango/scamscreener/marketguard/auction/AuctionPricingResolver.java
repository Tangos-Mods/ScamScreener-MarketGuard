package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.error;

final class AuctionPricingResolver {
    private AuctionPricingResolver() {}

    record PricingData(String itemId, String displayName, double lowestBin, double playerPrice) {}

    static PricingData resolve(AuctionInteractEvent.Context context, ClientPlayerEntity player, boolean cancelOnFailure) {
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
            return abortPricing(context, player, Text.literal("Could not find Auction Item").formatted(Formatting.RED), cancelOnFailure);
        }

        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            MarketGuard.debug("Pricing resolution aborted: no SkyBlock ID found for '{}'", itemStack.getName().getString());
            return abortPricing(
                    context,
                    player,
                    Text.literal("Could not read Skyblock ID for ").append(itemStack.getName()).formatted(Formatting.RED),
                    cancelOnFailure
            );
        }
        MarketGuard.debug("Resolved SkyBlock item id='{}'", itemId);
        String displayName = itemStack.getName().getString();

        LowestBIN.LookupResult lookupResult = SkyBlockItemUtil.lookupLowestBin(itemId);
        if (!lookupResult.hasValue()) {
            MarketGuard.debug(
                    "Pricing resolution skipped: no cached Lowest BIN is available for '{}' stale={} loading={} refreshFailed={}",
                    itemId,
                    lookupResult.stale(),
                    lookupResult.loading(),
                    lookupResult.refreshFailed()
            );
            return null;
        }

        if (lookupResult.stale()) {
            MarketGuard.debug("Pricing resolution continues with stale Lowest BIN cache for '{}'", itemId);
        }

        double lowestBin = lookupResult.value();
        if (lowestBin <= 0.0) {
            MarketGuard.debug("Pricing resolution aborted: Lowest BIN was invalid for '{}' value={}", itemId, lowestBin);
            return abortPricing(
                    context,
                    player,
                    Text.literal("Lowest BIN is invalid for ").append(itemId).formatted(Formatting.RED),
                    cancelOnFailure
            );
        }
        MarketGuard.debug("Resolved Lowest BIN itemId='{}' value={}", itemId, lowestBin);

        try {
            double playerPrice = context.getPlayerPrice();
            MarketGuard.debug("Resolved player price itemId='{}' value={}", itemId, playerPrice);
            return new PricingData(itemId, displayName, lowestBin, playerPrice);
        } catch (Exception e) {
            MarketGuard.debug("Pricing resolution failed while reading player price for '{}' error='{}'", itemId, e.getMessage());
            return abortPricing(
                    context,
                    player,
                    Text.literal("Failed to catch item price: " + e.getMessage()).formatted(Formatting.RED),
                    cancelOnFailure
            );
        }
    }

    private static PricingData abortPricing(
            AuctionInteractEvent.Context context,
            ClientPlayerEntity player,
            Text message,
            boolean cancelOnFailure
    ) {
        if (cancelOnFailure) {
            context.cancel();
        }

        error(message, player);
        return null;
    }
}
