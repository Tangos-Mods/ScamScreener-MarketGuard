package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

final class SnapshotDataUtil {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private SnapshotDataUtil() {}

    static String findItemIdByName(JsonObject snapshot, String displayName, Function<JsonObject, String> itemNameReader) {
        if (displayName == null || displayName.isBlank() || snapshot == null) {
            return null;
        }

        String normalizedDisplayName = normalizeName(displayName);
        for (String itemId : snapshot.keySet()) {
            JsonElement entry = snapshot.get(itemId);
            if (entry == null || !entry.isJsonObject()) {
                continue;
            }

            String itemName = itemNameReader.apply(entry.getAsJsonObject());
            if (itemName == null) {
                continue;
            }

            if (normalizedDisplayName.equals(normalizeName(itemName))) {
                return itemId;
            }
        }

        return null;
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
