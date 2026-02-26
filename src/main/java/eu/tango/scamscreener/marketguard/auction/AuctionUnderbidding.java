package eu.tango.scamscreener.marketguard.auction;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.util.SkyBlockItemUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.PREFIX;

public final class AuctionUnderbidding {
    private static final double percentage = 0.33;
    private AuctionUnderbidding() {}

    public static void onInteract(AuctionInteractEvent.Context context) {
        if (!isCreateBinClick(context)) return;
        context.cancel();

        ItemStack itemStack = getAuctionItemStack(context);
        if (itemStack.isEmpty()) {
            sendStatus(context, "Konnte Auktion-Item nicht lesen.", Formatting.RED);
            return;
        }


        String itemId = SkyBlockItemUtil.getSkyblockId(itemStack);
        if (itemId == null) {
            sendStatus(context, "Konnte SkyBlock Item-ID fuer " + itemStack.getItem() + " nicht lesen.", Formatting.RED);
            return;
        }

        CompletableFuture
                .supplyAsync(() -> fetchLowestBin(itemId))
                .thenAccept(lowestBin -> context.getMc().execute(() -> {
                    if (lowestBin == null) {
                        sendStatus(context, "Lowest BIN fuer " + itemId + " konnte nicht geladen werden.", Formatting.RED);
                        return;
                    }

                    // TODO: cancel Logik einbauen, dass wenn item unter der percentage reingesetzt wurde, der Click gecancelled wird

                }));
    }

    private static boolean isCreateBinClick(AuctionInteractEvent.Context context) {
        String inventoryName = context.getInventoryName();
        if (inventoryName == null) return false;
        if (!inventoryName.contains(AuctionInventory.CREATE_BIN.getTitle())) return false;

        return context.getSlotId() == AuctionSlots.CREATE_BIN.getSlot();
    }

    private static ItemStack getAuctionItemStack(AuctionInteractEvent.Context context) {
        int slotIndex = AuctionSlots.ITEM.getSlot();
        if (slotIndex < 0 || slotIndex >= context.getScreenHandler().slots.size()) return ItemStack.EMPTY;
        return context.getScreenHandler().getSlot(slotIndex).getStack();
    }

    private static Double fetchLowestBin(String itemId) {
        try {
            return LowestBIN.getLowestBIN(itemId);
        } catch (Exception e) {
            MarketGuard.LOGGER.error("Lowest BIN fetch failed for '{}': {}", itemId, e.getMessage(), e);
            return null;
        }
    }

    private static void sendStatus(AuctionInteractEvent.Context context, String message, Formatting color) {
        if (context.getMc().player == null) return;
        Text text = PREFIX.copy().append(Text.literal(message).formatted(color));
        context.getMc().player.sendMessage(text, false);
    }

}
