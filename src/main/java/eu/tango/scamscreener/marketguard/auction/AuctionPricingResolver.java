package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.error;

final class AuctionPricingResolver {
    private AuctionPricingResolver() {}

    record PricingData(String itemId, String displayName, double lowestBin, double playerPrice) {}

    static PricingData resolve(AuctionInteractEvent.Context context, LocalPlayer player, boolean cancelOnFailure) {
        ItemStack itemStack = context.getAuctionItemStack();
        MarketGuard.debug(
                "Resolving pricing title='{}' clickedSlot={} actionType={} auctionItem='{}'",
                context.getInventoryName(),
                context.getSlotId(),
                context.getActionType(),
                itemStack.isEmpty() ? "<empty>" : itemStack.getHoverName().getString()
        );
        if (itemStack.isEmpty()) {
            MarketGuard.debug("Pricing resolution aborted: auction item stack was empty");
            return abortPricing(context, player, Component.literal("Could not find Auction Item").withStyle(ChatFormatting.RED), cancelOnFailure);
        }

        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            MarketGuard.debug("Pricing resolution aborted: no SkyBlock ID found for '{}'", itemStack.getHoverName().getString());
            return abortPricing(
                    context,
                    player,
                    Component.literal("Could not read Skyblock ID for ").append(itemStack.getHoverName()).withStyle(ChatFormatting.RED),
                    cancelOnFailure
            );
        }
        MarketGuard.debug("Resolved SkyBlock item id='{}'", itemId);
        String displayName = SkyBlockItemUtil.getDisplayName(itemStack);

        LowestBinData.LookupResult lookupResult = LowestBinData.lookupLowestBin(itemId);
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
                    Component.literal("Lowest BIN is invalid for ").append(itemId).withStyle(ChatFormatting.RED),
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
                    Component.literal("Failed to catch item price: " + e.getMessage()).withStyle(ChatFormatting.RED),
                    cancelOnFailure
            );
        }
    }

    private static PricingData abortPricing(
            AuctionInteractEvent.Context context,
            LocalPlayer player,
            Component message,
            boolean cancelOnFailure
    ) {
        if (cancelOnFailure) {
            context.cancel();
        }

        error(message, player);
        return null;
    }
}
