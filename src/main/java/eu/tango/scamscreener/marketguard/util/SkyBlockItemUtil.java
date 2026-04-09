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
        MarketGuard.debug("Reading player price from slot item name='{}'", raw);
        Matcher m = Pattern.compile(AuctionSlots.ITEM_PRICE.getItemName()).matcher(raw);
        if (!m.find()) throw new Exception("Cannot read item price: " + raw);
        String matchedPrice = m.group();
        double parsedPrice = Double.parseDouble(matchedPrice.replaceAll("[^0-9,]", "").replace(",", ""));
        MarketGuard.debug("Parsed player price rawMatch='{}' parsed={}", matchedPrice, parsedPrice);
        return parsedPrice;
    }

    @Nullable
    private static String getSkyblockIdFromCompound(@Nullable NbtCompound compound) {
        if (compound == null) return null;
        String id = compound.getString("id").orElse(null);
        if (id == null || id.isBlank()) return null;
        if ("PET".equals(id)) {
            String petSkyBlockId = getPetSkyblockId(compound);
            return (petSkyBlockId == null || petSkyBlockId.isBlank()) ? id : petSkyBlockId;
        }
        if ("RUNE".equals(id)) {
            String runeSkyBlockId = getRuneSkyblockId(compound);
            return (runeSkyBlockId == null || runeSkyBlockId.isBlank()) ? id : runeSkyBlockId;
        }
        return id;
    }

    @Nullable
    private static String getRuneSkyblockId(NbtCompound compound) {
        NbtCompound runesCompound = compound.getCompound("runes").orElse(null);
        if (runesCompound == null) return null;

        for (String runeType : runesCompound.getKeys()) {
            if (runeType == null || runeType.isBlank()) continue;

            int runeLevel = runesCompound.getInt(runeType).orElse(0);
            if (runeLevel <= 0) continue;

            String normalizedRuneType = runeType.toUpperCase(Locale.ROOT);
            if (!normalizedRuneType.endsWith("_RUNE")) {
                normalizedRuneType += "_RUNE";
            }
            return normalizedRuneType + ";" + runeLevel;
        }

        return null;
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

    public static LowestBIN.LookupResult lookupLowestBin(String itemId) {
        return LowestBIN.lookupLowestBIN(itemId);
    }


}
