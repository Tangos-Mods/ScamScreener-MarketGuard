package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.*;

public final class AuctionUnderbidding {
    private static final double percentage = 0.80;
    private AuctionUnderbidding() {}

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!context.isCreateBinClick()) return;
        assert context.getMc().player != null;

        // DEBUG: always cancel
        //context.cancel();

        ItemStack itemStack = context.getAuctionItemStack();
        if (itemStack.isEmpty()) {
            error(Text.literal("Could not find Auction Item"), context.getMc().player);
            return;
        }
        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);

        if (itemId == null) {
            error(Text.literal("Could not read Skyblock ID for ").append(itemStack.getName()), context.getMc().player);
            return;
        }

        CompletableFuture
                .supplyAsync(() -> fetchLowestBin(itemId))
                .thenAccept(lowestBin -> context.getMc().execute(() -> {
                    if (lowestBin == null) {
                        error(Text.literal("Cannot load Lowest BIN for ").append(itemId), context.getMc().player);
                        return;
                    }
                    try {
                        double binPlayer = context.getPlayerPrice();
                        if (lowestBin <= 0.0) {
                            error(Text.literal("Lowest BIN is invalid for ").append(itemId), context.getMc().player);
                            return;
                        }

                        double minimumAllowedPrice = lowestBin * percentage;
                        double underbidPercent = ((lowestBin - binPlayer) / lowestBin) * 100.0;

                        if (binPlayer < minimumAllowedPrice) {
                            context.cancel();
                            underbidding(context.getAuctionItemId(), underbidPercent, context.getMc().player);
                        }
                    } catch (Exception e) {
                        error(Text.literal("Failed to catch item price: " + e.getMessage()), context.getMc().player);
                        //throw new RuntimeException(e);
                    }
                }));
    }


    private static Double fetchLowestBin(String itemId) {
        try {
            return LowestBIN.getLowestBIN(itemId);
        } catch (Exception e) {
            MarketGuard.LOGGER.error("Lowest BIN fetch failed for '{}': {}", itemId, e.getMessage(), e);
            return null;
        }
    }


}
