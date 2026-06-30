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

    private static String getSkyblockIdFromCompound(CompoundTag compound) throws Exception {
        Method method = SkyBlockItemUtil.class.getDeclaredMethod("getSkyblockIdFromCompound", CompoundTag.class);
        method.setAccessible(true);
        return (String) method.invoke(null, compound);
    }
}
