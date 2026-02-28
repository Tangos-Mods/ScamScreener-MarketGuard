package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.error;

final class AuctionPricingResolver {
    private AuctionPricingResolver() {}

    record PricingData(String itemId, double lowestBin, double playerPrice) {}

    static PricingData resolve(AuctionInteractEvent.Context context) {
        ClientPlayerEntity player = context.getMc().player;
        if (player == null) return null;

        ItemStack itemStack = context.getAuctionItemStack();
        if (itemStack.isEmpty()) {
            error(Text.literal("Could not find Auction Item"), player);
            return null;
        }

        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            error(Text.literal("Could not read Skyblock ID for ").append(itemStack.getName()), player);
            return null;
        }

        Double lowestBin = SkyBlockItemUtil.fetchLowestBin(itemId);
        if (lowestBin == null) {
            error(Text.literal("Cannot load Lowest BIN for ").append(itemId), player);
            return null;
        }
        if (lowestBin <= 0.0) {
            error(Text.literal("Lowest BIN is invalid for ").append(itemId), player);
            return null;
        }

        try {
            double playerPrice = context.getPlayerPrice();
            return new PricingData(itemId, lowestBin, playerPrice);
        } catch (Exception e) {
            error(Text.literal("Failed to catch item price: " + e.getMessage()), player);
            return null;
        }
    }
}
