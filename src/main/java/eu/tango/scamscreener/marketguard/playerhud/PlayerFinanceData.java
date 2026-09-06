package eu.tango.scamscreener.marketguard.playerhud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.ApiEndpoint;
import eu.tango.scamscreener.marketguard.MarketGuard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class PlayerFinanceData {
    private static final String URL = ApiEndpoint.url("/api/v1/player-finance");
    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ConcurrentHashMap<Key, Cached> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Key, CompletableFuture<LookupResult>> REQUESTS = new ConcurrentHashMap<>();
    private static volatile Function<Key, CompletableFuture<Response>> requester = PlayerFinanceData::requestFromApi;

    private PlayerFinanceData() {}

    public record Finance(Double bank, Double purse, Double museumValue, Double knownTotal) {}

    public record Museum(
            Double value,
            Boolean appraisal,
            List<String> donatedIds,
            Integer donatedCount,
            List<String> specialIds,
            Integer specialCount
    ) {
        public Museum {
            donatedIds = donatedIds == null ? List.of() : List.copyOf(donatedIds);
            specialIds = specialIds == null ? List.of() : List.copyOf(specialIds);
        }
    }

    public record Profile(String id, String name, Boolean selected, Finance finance, Museum museum) {}

    public record Response(
            String status,
            boolean stale,
            Long fetchedAt,
            String playerUuid,
            Profile profile,
            List<String> unavailableFields
    ) {
        public Response {
            unavailableFields = unavailableFields == null ? List.of() : List.copyOf(unavailableFields);
        }

        public boolean usable() {
            return ("ok".equals(status) || "partial".equals(status)) && profile != null;
        }

        public boolean fieldAvailable(String path) {
            if (!usable()) {
                return false;
            }
            String requested = normalizePath(path);
            for (String field : unavailableFields) {
                String unavailable = normalizePath(field);
                if (requested.equals(unavailable)
                        || requested.startsWith(unavailable + ".")
                        || unavailable.startsWith(requested + ".")) {
                    return false;
                }
            }
            return true;
        }
    }

    public record LookupResult(Response value, boolean stale, boolean loading, boolean refreshFailed) {
        public boolean hasValue() {
            return value != null;
        }
    }

    public static LookupResult lookupCached(String playerUuid, String profileId) {
        Key key = Key.of(playerUuid, profileId);
        if (key == null) {
            return new LookupResult(null, false, false, false);
        }
        return cached(key, isLoading(key), false);
    }

    public static CompletableFuture<LookupResult> request(String playerUuid, String profileId) {
        Key key = Key.of(playerUuid, profileId);
        if (key == null) {
            return CompletableFuture.completedFuture(new LookupResult(null, false, false, true));
        }

        LookupResult cached = cached(key, false, false);
        if (cached.hasValue() && !cached.stale()) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<LookupResult> existing = REQUESTS.get(key);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<LookupResult> result = new CompletableFuture<>();
        existing = REQUESTS.putIfAbsent(key, result);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Response> request;
        try {
            request = requester.apply(key);
        } catch (RuntimeException error) {
            REQUESTS.remove(key, result);
            result.complete(cached(key, false, true));
            return result;
        }
        request.whenComplete((response, error) -> {
            if (error == null && !matches(key, response)) {
                error = new IllegalStateException("Player finance response identifiers did not match the request");
            }
            if (error == null) {
                CACHE.put(key, new Cached(response, System.currentTimeMillis()));
                result.complete(cached(key, false, false));
            } else {
                MarketGuard.debug("Player finance request failed for '{}': {}", key.playerUuid(), error.getMessage());
                result.complete(cached(key, false, true));
            }
            REQUESTS.remove(key, result);
        });
        return result;
    }

    private static boolean matches(Key key, Response response) {
        if (response == null || !key.playerUuid().equals(Key.compactId(response.playerUuid()))) {
            return false;
        }
        return response.profile() == null
                || key.profileId().equals(Key.compactId(response.profile().id()));
    }

    static Response parseResponse(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException("Player finance request failed with status " + statusCode);
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Player finance response was not valid JSON", exception);
        }

        String status = text(root, "status");
        if (status == null) {
            throw new IllegalStateException("Player finance response had no status");
        }
        JsonObject profileJson = object(root, "profile");
        Profile profile = profileJson == null ? null : new Profile(
                text(profileJson, "id"),
                text(profileJson, "name"),
                bool(profileJson, "selected"),
                finance(object(profileJson, "finance")),
                museum(object(profileJson, "museum"))
        );
        return new Response(
                status,
                Boolean.TRUE.equals(bool(root, "stale")),
                integer(root, "fetchedAt"),
                text(root, "playerUuid"),
                profile,
                strings(array(root, "unavailableFields"))
        );
    }

    private static CompletableFuture<Response> requestFromApi(Key key) {
        JsonObject body = new JsonObject();
        body.addProperty("playerUuid", key.playerUuid());
        body.addProperty("profileId", key.profileId());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Content-Type", "application/json")
                .header("User-Agent", MarketGuard.userAgent())
                .timeout(Duration.ofSeconds(8))
                .method("QUERY", HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponse(response.statusCode(), response.body()));
    }

    private static LookupResult cached(Key key, boolean loading, boolean refreshFailed) {
        Cached cached = CACHE.get(key);
        if (cached == null) {
            return new LookupResult(null, false, loading, refreshFailed);
        }
        boolean stale = cached.response().stale()
                || System.currentTimeMillis() - cached.cachedAt() >= CACHE_TTL_MILLIS;
        return new LookupResult(cached.response(), stale, loading, refreshFailed);
    }

    private static boolean isLoading(Key key) {
        CompletableFuture<?> request = REQUESTS.get(key);
        return request != null && !request.isDone();
    }

    private static Finance finance(JsonObject value) {
        return value == null ? null : new Finance(
                number(value, "bank"),
                number(value, "purse"),
                number(value, "museumValue"),
                number(value, "knownTotal")
        );
    }

    private static Museum museum(JsonObject value) {
        return value == null ? null : new Museum(
                number(value, "value"),
                bool(value, "appraisal"),
                strings(array(value, "donatedIds")),
                count(value, "donatedCount"),
                strings(array(value, "specialIds")),
                count(value, "specialCount")
        );
    }

    private static String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.startsWith("profile.") ? normalized.substring("profile.".length()) : normalized;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key)
                : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray()
                ? parent.getAsJsonArray(key)
                : null;
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            String value = object.get(key).getAsString();
            return value == null || value.isBlank() ? null : value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Boolean bool(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Double number(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            double value = object.get(key).getAsDouble();
            return Double.isFinite(value) && value >= 0.0 ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Long integer(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            long value = object.get(key).getAsLong();
            return value >= 0L ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Integer count(JsonObject object, String key) {
        Long value = integer(object, key);
        return value != null && value <= Integer.MAX_VALUE ? value.intValue() : null;
    }

    private static List<String> strings(JsonArray values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                String text = value.getAsString();
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
        }
        return List.copyOf(result);
    }

    static void resetForTests() {
        CACHE.clear();
        REQUESTS.clear();
        requester = PlayerFinanceData::requestFromApi;
    }

    static void setRequesterForTests(Function<Key, CompletableFuture<Response>> value) {
        requester = value;
    }

    record Key(String playerUuid, String profileId) {
        private static Key of(String playerUuid, String profileId) {
            String uuid = compactId(playerUuid);
            String profile = compactId(profileId);
            return uuid == null || profile == null ? null : new Key(uuid, profile);
        }

        private static String compactId(String value) {
            if (value == null) {
                return null;
            }
            String compact = value.trim().replace("-", "").toLowerCase(Locale.ROOT);
            return compact.matches("[0-9a-f]{32}") ? compact : null;
        }
    }

    private record Cached(Response response, long cachedAt) {}
}
