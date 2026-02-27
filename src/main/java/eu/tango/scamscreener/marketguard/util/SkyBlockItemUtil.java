package eu.tango.scamscreener.marketguard.util;

import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyBlockItemUtil {

    @Nullable
    public static String getSkyblockId(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return null;

        NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;

        NbtCompound nbt = customData.copyNbt();

        String id = getId(nbt.getCompound("minecraft:custom_data").orElse(null));
        if (isSkyBlockId(id)) return id;

        id = getId(nbt.getCompound("ExtraAttributes").orElse(null));
        if (isSkyBlockId(id)) return id;

        id = getId(nbt);
        if (isSkyBlockId(id)) return id;

        return null;
    }

    public static double getPriceFromNBT(ItemStack item) throws Exception {
        if (item == null || item.isEmpty()) throw new Exception("Item cannot be empty");

        String raw = item.getName().getString();
        Matcher m = Pattern.compile(AuctionSlots.ITEM_PRICE.getItemName()).matcher(raw);
        if (!m.find()) throw new Exception("Cannot read item price: " + raw);
        return Double.parseDouble(m.group().replaceAll("[^0-9,]", "").replace(",", ""));
    }

    @Nullable
    private static String getId(@Nullable NbtCompound compound) {
        if (compound == null) return null;
        String id = compound.getString("id").orElse(null);
        if (id == null || id.isBlank()) return null;
        return id;
    }

    private static boolean isSkyBlockId(@Nullable String id) {
        return id != null && !id.isBlank() && !id.contains(":");
    }

}
