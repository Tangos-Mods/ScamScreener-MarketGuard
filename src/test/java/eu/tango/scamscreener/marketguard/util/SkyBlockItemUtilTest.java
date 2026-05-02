package eu.tango.scamscreener.marketguard.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyBlockItemUtilTest {

    @Test
    void getSkyblockIdFromCompoundBuildsRuneApiKey() throws Exception {
        NbtCompound runes = new NbtCompound();
        runes.putInt("snow", 1);

        NbtCompound extraAttributes = new NbtCompound();
        extraAttributes.putString("id", "RUNE");
        extraAttributes.put("runes", runes);

        assertEquals("SNOW_RUNE;1", getSkyblockIdFromCompound(extraAttributes));
    }

    @Test
    void getDisplayNameUsesThirdTooltipLineForAuctionPlaceholder() {
        assertEquals(
                "Egg Pile",
                SkyBlockItemUtil.resolveDisplayName("AUCTION FOR ITEM:", List.of(
                Text.literal(""),
                Text.literal("Egg Pile"),
                Text.literal("Furniture")
        ))
        );
    }

    @Test
    void getDisplayNameReturnsNormalItemNameWhenNotPlaceholder() {
        assertEquals("Fancy Leggings", SkyBlockItemUtil.resolveDisplayName("Fancy Leggings", List.of()));
    }

    private static String getSkyblockIdFromCompound(NbtCompound compound) throws Exception {
        Method method = SkyBlockItemUtil.class.getDeclaredMethod("getSkyblockIdFromCompound", NbtCompound.class);
        method.setAccessible(true);
        return (String) method.invoke(null, compound);
    }
}
