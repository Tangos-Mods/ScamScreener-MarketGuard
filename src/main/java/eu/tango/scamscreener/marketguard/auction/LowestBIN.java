package eu.tango.scamscreener.marketguard.auction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LowestBIN {

    private static final String URL = "https://moulberry.codes/lowestbin.json";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Object LOCK = new Object();

    // Cache full lowestbin.json snapshot for 60 seconds.
    private static final long TTL_MS = 60_000;
    private static volatile JsonObject cachedSnapshot;
    private static volatile long cacheExpiresAtMs = 0L;

    public static double getLowestBIN(String itemId) throws Exception {
        JsonObject snapshot = getSnapshot();
        if (!snapshot.has(itemId)) throw new Exception("Item not found");
        return snapshot.get(itemId).getAsDouble();
    }

    private static JsonObject getSnapshot() throws Exception {
        long now = System.currentTimeMillis();
        JsonObject snapshot = cachedSnapshot;
        if (snapshot != null && now < cacheExpiresAtMs) {
            return snapshot;
        }

        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (cachedSnapshot != null && now < cacheExpiresAtMs) {
                return cachedSnapshot;
            }

            JsonObject fresh = fetchLowestBinSnapshot();
            cachedSnapshot = fresh;
            cacheExpiresAtMs = now + TTL_MS;
            return fresh;
        }
    }

    private static JsonObject fetchLowestBinSnapshot() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
