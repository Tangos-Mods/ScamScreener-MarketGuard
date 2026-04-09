package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("live-api")
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class LowestBINApiTest {
    private static final Pattern SIMPLE_ITEM_KEY = Pattern.compile("^[A-Z0-9_]+$");
    private static final Pattern PET_TIER_KEY = Pattern.compile("^[A-Z0-9_]+;[1-9][0-9]*$");
    private static final Pattern PET_LEVEL_KEY = Pattern.compile("^[A-Z0-9_]+;[1-9][0-9]*\\+[1-9][0-9]*$");
    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("^[A-Z0-9_]+\\+ATTRIBUTE_[A-Z0-9_]+(?:;[1-9][0-9]*|\\+ATTRIBUTE_[A-Z0-9_]+.*)?$");

    @Test
    void endpointReturnsNonEmptySnapshot() throws Exception {
        JsonObject snapshot = getSnapshot();

        assertTrue(snapshot.size() > 0, "lowestbin snapshot should not be empty");
        assertTrue(snapshot.has("ABICASE"), "snapshot should contain a known simple item");
    }

    @Test
    void returnsPriceForSimpleItem() throws Exception {
        String itemKey = findMatchingKey(SIMPLE_ITEM_KEY);
        double price = LowestBIN.getLowestBIN(itemKey);

        assertTrue(price > 0.0, "simple item should return a positive Lowest BIN");
    }

    @Test
    void returnsPriceForPetKeyWithTierSuffix() throws Exception {
        String itemKey = findMatchingKey(PET_TIER_KEY);
        double price = LowestBIN.getLowestBIN(itemKey);

        assertTrue(price > 0.0, "pet key with tier suffix should return a positive Lowest BIN");
    }

    @Test
    void returnsPriceForPetKeyWithLevelMetadata() throws Exception {
        String itemKey = findMatchingKey(PET_LEVEL_KEY);
        double price = LowestBIN.getLowestBIN(itemKey);

        assertTrue(price > 0.0, "pet key with level metadata should return a positive Lowest BIN");
    }

    @Test
    void returnsPriceForItemKeyWithAttributeMetadata() throws Exception {
        String itemKey = findMatchingKey(ATTRIBUTE_KEY);
        double price = LowestBIN.getLowestBIN(itemKey);

        assertTrue(price > 0.0, "item key with attribute metadata should return a positive Lowest BIN");
    }

    private static JsonObject getSnapshot() throws Exception {
        Method method = LowestBIN.class.getDeclaredMethod("getSnapshot");
        method.setAccessible(true);
        return (JsonObject) method.invoke(null);
    }

    private static String findMatchingKey(Pattern pattern) throws Exception {
        Set<String> keys = getSnapshot().keySet();

        for (String key : keys) {
            if (pattern.matcher(key).matches()) {
                return key;
            }
        }

        throw new AssertionError("No live API key matched pattern: " + pattern);
    }
}
