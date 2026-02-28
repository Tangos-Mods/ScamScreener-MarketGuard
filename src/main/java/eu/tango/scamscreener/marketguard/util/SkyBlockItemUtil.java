package eu.tango.scamscreener.marketguard.util;

import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.auction.AuctionSlots;
import eu.tango.scamscreener.marketguard.auction.LowestBIN;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyBlockItemUtil {
    private static final Pattern PET_TYPE_PATTERN = Pattern.compile("\"?type\"?\\s*:\\s*\"?([A-Za-z0-9_]+)\"?");
    private static final Pattern PET_TIER_PATTERN = Pattern.compile("\"?tier\"?\\s*:\\s*\"?([A-Za-z_]+)\"?");

    @Nullable
    public static String getSkyblockId(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return null;

        NbtComponent customData = itemStack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;

        NbtCompound nbt = customData.copyNbt();

        String id = getSkyblockIdFromCompound(nbt.getCompound("minecraft:custom_data").orElse(null));
        if (isSkyBlockId(id)) return id;

        id = getSkyblockIdFromCompound(nbt.getCompound("ExtraAttributes").orElse(null));
        if (isSkyBlockId(id)) return id;

        id = getSkyblockIdFromCompound(nbt);
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
    private static String getSkyblockIdFromCompound(@Nullable NbtCompound compound) {
        if (compound == null) return null;
        String id = compound.getString("id").orElse(null);
        if (id == null || id.isBlank()) return null;
        if (!"PET".equals(id)) return id;

        String petSkyBlockId = getPetSkyblockId(compound);
        return (petSkyBlockId == null || petSkyBlockId.isBlank()) ? id : petSkyBlockId;
    }

    @Nullable
    private static String getPetSkyblockId(NbtCompound compound) {
        String petType = getPetType(compound);
        if (petType == null || petType.isBlank()) return null;

        String petTierName = getPetTierName(compound);
        ItemTier petTier = ItemTier.fromName(petTierName);
        if (petTier == null) return petType;

        return petType + ";" + petTier.getTier();
    }

    @Nullable
    private static String getPetType(NbtCompound compound) {
        NbtCompound petInfoCompound = compound.getCompound("petInfo").orElse(null);
        if (petInfoCompound != null) {
            String type = petInfoCompound.getString("type").orElse(null);
            if (type != null && !type.isBlank()) return type.toUpperCase(Locale.ROOT);
        }

        String petInfoRaw = compound.getString("petInfo").orElse(null);
        if (petInfoRaw == null || petInfoRaw.isBlank()) return null;

        Matcher matcher = PET_TYPE_PATTERN.matcher(petInfoRaw);
        if (!matcher.find()) return null;

        String type = matcher.group(1);
        if (type == null || type.isBlank()) return null;
        return type.toUpperCase(Locale.ROOT);
    }

    @Nullable
    private static String getPetTierName(NbtCompound compound) {
        NbtCompound petInfoCompound = compound.getCompound("petInfo").orElse(null);
        if (petInfoCompound != null) {
            String tier = petInfoCompound.getString("tier").orElse(null);
            if (tier != null && !tier.isBlank()) return tier;
        }

        String petInfoRaw = compound.getString("petInfo").orElse(null);
        if (petInfoRaw == null || petInfoRaw.isBlank()) return null;

        Matcher matcher = PET_TIER_PATTERN.matcher(petInfoRaw);
        if (!matcher.find()) return null;

        String tier = matcher.group(1);
        if (tier == null || tier.isBlank()) return null;
        return tier;
    }

    private static boolean isSkyBlockId(@Nullable String id) {
        return id != null && !id.isBlank() && !id.contains(":");
    }

    public static Double fetchLowestBin(String itemId) {
        try {
            return LowestBIN.getLowestBIN(itemId);
        } catch (Exception e) {
            MarketGuard.LOGGER.error("Lowest BIN fetch failed for '{}': {}", itemId, e.getMessage(), e);
            return null;
        }
    }


}
