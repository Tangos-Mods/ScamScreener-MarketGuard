package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

final class SnapshotDataUtil {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private SnapshotDataUtil() {}

    static Map<String, String> indexItemIdsByName(JsonObject snapshot, Function<JsonObject, String> itemNameReader) {
        Map<String, String> itemIdsByName = new HashMap<>();
        for (String itemId : snapshot.keySet()) {
            JsonElement entry = snapshot.get(itemId);
            if (entry == null || !entry.isJsonObject()) {
                continue;
            }

            String itemName = itemNameReader.apply(entry.getAsJsonObject());
            if (itemName == null) {
                continue;
            }

            itemIdsByName.putIfAbsent(normalizeName(itemName), itemId);
        }

        return itemIdsByName;
    }

    static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        return WHITESPACE_PATTERN.matcher(value).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
