package eu.tango.scamscreener.marketguard.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

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
