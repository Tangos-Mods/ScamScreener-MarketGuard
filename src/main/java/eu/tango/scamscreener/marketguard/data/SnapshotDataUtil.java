package eu.tango.scamscreener.marketguard.data;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.function.Function;

final class SnapshotDataUtil {
    private SnapshotDataUtil() {}

    static String findItemIdByName(JsonObject snapshot, String displayName, Function<JsonObject, String> itemNameReader) {
        if (displayName == null || displayName.isBlank() || snapshot == null) {
            return null;
        }

        String normalizedDisplayName = normalizeName(displayName);
        for (String itemId : snapshot.keySet()) {
            if (!snapshot.get(itemId).isJsonObject()) {
                continue;
            }

            String itemName = itemNameReader.apply(snapshot.getAsJsonObject(itemId));
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
        return value == null ? "" : value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
