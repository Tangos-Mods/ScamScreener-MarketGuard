package eu.tango.scamscreener.marketguard.util;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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

    private static String getSkyblockIdFromCompound(NbtCompound compound) throws Exception {
        Method method = SkyBlockItemUtil.class.getDeclaredMethod("getSkyblockIdFromCompound", NbtCompound.class);
        method.setAccessible(true);
        return (String) method.invoke(null, compound);
    }
}
