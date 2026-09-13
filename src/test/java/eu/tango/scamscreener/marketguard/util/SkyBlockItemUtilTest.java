package eu.tango.scamscreener.marketguard.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyBlockItemUtilTest {

    @Test
    void getSkyblockIdFromCompoundBuildsRuneApiKey() throws Exception {
        CompoundTag runes = new CompoundTag();
        runes.putInt("snow", 1);

        CompoundTag extraAttributes = new CompoundTag();
        extraAttributes.putString("id", "RUNE");
        extraAttributes.put("runes", runes);

        assertEquals("SNOW_RUNE;1", getSkyblockIdFromCompound(extraAttributes));
    }

    @Test
    void getSkyblockIdFromCompoundUsesZeroBasedPetTier() throws Exception {
        assertEquals("ENDERMAN;0", getSkyblockIdFromCompound(petExtraAttributes("ENDERMAN", "COMMON")));
        assertEquals("ENDERMAN;1", getSkyblockIdFromCompound(petExtraAttributes("ENDERMAN", "UNCOMMON")));
        assertEquals("ENDERMAN;4", getSkyblockIdFromCompound(petExtraAttributes("ENDERMAN", "LEGENDARY")));
    }

    @Test
    void getSkyblockIdAppendsLevel100SuffixForMaxLevelPets() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("ExtraAttributes", petExtraAttributes("BEE", "LEGENDARY"));

        assertEquals("BEE;4+100", SkyBlockItemUtil.getSkyblockId(nbt, "[Lvl 100] Bee"));
        assertEquals("BEE;4", SkyBlockItemUtil.getSkyblockId(nbt, "[Lvl 1] Bee"));
        assertEquals("BEE;4", SkyBlockItemUtil.getSkyblockId(nbt, null));
    }

    @Test
    void getDisplayNameUsesThirdTooltipLineForAuctionPlaceholder() {
        assertEquals(
                "Egg Pile",
                SkyBlockItemUtil.resolveDisplayName("AUCTION FOR ITEM:", List.of(
                Component.literal(""),
                Component.literal("Egg Pile"),
                Component.literal("Furniture")
        ))
        );
    }

    @Test
    void getDisplayNameReturnsNormalItemNameWhenNotPlaceholder() {
        assertEquals("Fancy Leggings", SkyBlockItemUtil.resolveDisplayName("Fancy Leggings", List.of()));
    }

    @Test
    void parsesBuyItNowPriceFromBinAuctionLore() {
        assertEquals(4_242_911_000D, SkyBlockItemUtil.parsePrice("Buy it now: 4,242,911,000 coins"));
    }

    private static CompoundTag petExtraAttributes(String type, String tier) {
        CompoundTag petInfo = new CompoundTag();
        petInfo.putString("type", type);
        petInfo.putString("tier", tier);

        CompoundTag extraAttributes = new CompoundTag();
        extraAttributes.putString("id", "PET");
        extraAttributes.put("petInfo", petInfo);
        return extraAttributes;
    }

    private static String getSkyblockIdFromCompound(CompoundTag compound) throws Exception {
        Method method = SkyBlockItemUtil.class.getDeclaredMethod("getSkyblockIdFromCompound", CompoundTag.class);
        method.setAccessible(true);
        return (String) method.invoke(null, compound);
    }
}
