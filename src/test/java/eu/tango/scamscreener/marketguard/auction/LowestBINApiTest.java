package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("live-api")
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class LowestBINApiTest {

    @Test
    void endpointReturnsNonEmptySnapshot() throws Exception {
        JsonObject snapshot = getSnapshot();

        assertTrue(snapshot.size() > 0, "lowestbin snapshot should not be empty");
        assertTrue(snapshot.has("ABICASE"), "snapshot should contain a known simple item");
    }

    @Test
    void returnsPriceForSimpleItem() throws Exception {
        double price = LowestBIN.getLowestBIN("ABICASE");

        assertTrue(price > 0.0, "simple item should return a positive Lowest BIN");
    }

    @Test
    void returnsPriceForPetKeyWithTierSuffix() throws Exception {
        double price = LowestBIN.getLowestBIN("SHEEP;4");

        assertTrue(price > 0.0, "pet key with tier suffix should return a positive Lowest BIN");
    }

    @Test
    void returnsPriceForPetKeyWithLevelMetadata() throws Exception {
        double price = LowestBIN.getLowestBIN("ARMADILLO;5+100");

        assertTrue(price > 0.0, "pet key with level metadata should return a positive Lowest BIN");
    }

    @Test
    void returnsPriceForItemKeyWithAttributeMetadata() throws Exception {
        double price = LowestBIN.getLowestBIN("AURORA_BOOTS+ATTRIBUTE_BLAZING_RESISTANCE;1");

        assertTrue(price > 0.0, "item key with attribute metadata should return a positive Lowest BIN");
    }

    private static JsonObject getSnapshot() throws Exception {
        Method method = LowestBIN.class.getDeclaredMethod("getSnapshot");
        method.setAccessible(true);
        return (JsonObject) method.invoke(null);
    }
}
