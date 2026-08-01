package eu.tango.scamscreener.marketguard.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.ApiEndpoint;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.api.MarketGuardApi;
import eu.tango.scamscreener.marketguard.api.PlayerApiUnavailableException;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.playerhud.EncounterTracker;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import net.fabricmc.loader.api.FabricLoader;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class PlayerHud {
    private static final String PLAYERS_URL = ApiEndpoint.url("/api/v1/players");
    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final DateTimeFormatter FETCHED_AT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter FIRST_JOIN_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final AtomicLong REQUEST_ID = new AtomicLong();
    private static volatile View view = View.hidden();
    private static volatile Preset preset = Preset.TRADE;
    private static volatile ErrorDetails errorDetails;
    private static final ConcurrentHashMap<CacheKey, CachedPlayer> CACHED_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> RESOLVED_UUIDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<RequestKey, CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>>> PLAYER_REQUESTS = new ConcurrentHashMap<>();
    private static volatile Function<Target, PlayerResponse> playerRequester = PlayerHud::requestPlayerFromApi;

    private PlayerHud() {}

    public static void initialize() {
        setPreset(MarketGuardConfig.getPlayerHudPreset());
        MarketGuard.LOGGER.info("Player HUD API endpoint: {}", ApiEndpoint.baseUrl());
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, GLFW.GLFW_KEY_F8, Widgets.class, true);
    }

    public static void show(String player, String profileId) {
        Target target = target(player, profileId);
        if (target == null) {
            return;
        }

        long requestId = REQUEST_ID.incrementAndGet();
        errorDetails = null;
        view = View.loading(target);
        requestPlayer(target).whenComplete((result, error) -> {
            if (REQUEST_ID.get() != requestId) {
                return;
            }
            if (error != null) {
                view = View.error(target);
                return;
            }
            if (!result.hasValue()) {
                view = View.error(target);
                return;
            }

            MarketGuardApi.PlayerData playerData = result.value();
            view = View.ready(target, playerData.raw(), playerData.blacklisted(), playerData.apiStale());
        });
    }

    public static MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> lookupCachedPlayer(String player, String profileId) {
        Target target = target(player, profileId);
        if (target == null) {
            return new MarketGuardApi.CachedValue<>(null, false, false, false);
        }
        return cachedPlayer(target, isRequestInFlight(target), false);
    }

    public static CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>> requestPlayer(String player, String profileId) {
        Target target = target(player, profileId);
        if (target == null) {
            return CompletableFuture.completedFuture(new MarketGuardApi.CachedValue<>(null, false, false, false));
        }
        return requestPlayer(target);
    }

    private static CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>> requestPlayer(Target target) {
        MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> cached = cachedPlayer(target, false, false);
        if (cached.hasValue() && !cached.stale()) {
            return CompletableFuture.completedFuture(cached);
        }

        RequestKey requestKey = RequestKey.from(target);
        CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>> existing = PLAYER_REQUESTS.get(requestKey);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>> result = new CompletableFuture<>();
        existing = PLAYER_REQUESTS.putIfAbsent(requestKey, result);
        if (existing != null) {
            return existing;
        }

        CompletableFuture.supplyAsync(() -> playerRequester.apply(target)).whenComplete((response, error) -> {
            if (error != null) {
                MarketGuard.debug("Player request failed for '{}': {}", target.player(), error.getMessage());
                Throwable cause = unwrap(error);
                if (cause instanceof PlayerApiUnavailableException) {
                    result.completeExceptionally(cause);
                } else {
                    result.complete(cachedPlayer(target, false, true));
                }
            } else {
                result.complete(cachePlayer(target, response));
            }
            PLAYER_REQUESTS.remove(requestKey, result);
        });
        return result;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static void clear() {
        REQUEST_ID.incrementAndGet();
        errorDetails = null;
        view = View.hidden();
    }

    public static boolean setPreset(String name) {
        Preset resolved = Preset.fromConfig(name);
        if (resolved == null) {
            return false;
        }
        preset = resolved;
        return true;
    }

    private static String normalizeProfileId(String profileId) {
        return profileId == null || profileId.isBlank() ? null : profileId.trim();
    }

    private static String normalizePlayerKey(String player) {
        return player.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static Target target(String player, String profileId) {
        String normalizedPlayer = player == null ? "" : player.trim();
        return normalizedPlayer.isEmpty() ? null : new Target(normalizedPlayer, normalizeProfileId(profileId));
    }

    private static MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> cachedPlayer(
            Target target,
            boolean loading,
            boolean refreshFailed
    ) {
        String resolvedUuid = RESOLVED_UUIDS.get(normalizePlayerKey(target.player()));
        CachedPlayer cached = CACHED_PLAYERS.get(new CacheKey(
                resolvedUuid == null ? target.player() : resolvedUuid,
                target.profileId()
        ));
        if (cached == null) {
            return new MarketGuardApi.CachedValue<>(null, false, loading, refreshFailed);
        }

        boolean stale = System.currentTimeMillis() - cached.fetchedAt() >= CACHE_TTL_MILLIS;
        return new MarketGuardApi.CachedValue<>(toPlayerData(cached), stale, loading, refreshFailed);
    }

    private static MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> cachePlayer(Target target, PlayerResponse response) {
        JsonObject player = response.player();
        CachedPlayer cached = new CachedPlayer(player, isLocallyBlacklisted(player), response.stale(), System.currentTimeMillis());
        String uuid = text(player, "uuid", null);
        if (uuid != null) {
            RESOLVED_UUIDS.put(normalizePlayerKey(target.player()), uuid);
            String responseName = text(player, "name", null);
            if (responseName != null) {
                RESOLVED_UUIDS.put(normalizePlayerKey(responseName), uuid);
            }
            CACHED_PLAYERS.put(new CacheKey(uuid, target.profileId()), cached);
        }
        return new MarketGuardApi.CachedValue<>(toPlayerData(cached), false, false, false);
    }

    private static MarketGuardApi.PlayerData toPlayerData(CachedPlayer cached) {
        JsonObject player = cached.player();
        return new MarketGuardApi.PlayerData(
                text(player, "uuid", null),
                text(player, "name", null),
                text(player, "status", "unavailable"),
                cached.blacklisted(),
                cached.stale(),
                player
        );
    }

    private static boolean isRequestInFlight(Target target) {
        CompletableFuture<?> request = PLAYER_REQUESTS.get(RequestKey.from(target));
        return request != null && !request.isDone();
    }

    private static String requestBody(Target target) {
        JsonObject request = new JsonObject();
        JsonArray players = new JsonArray();
        JsonObject player = new JsonObject();
        player.addProperty("player", target.player());
        if (target.profileId() != null) {
            player.addProperty("profileId", target.profileId());
        }
        players.add(player);
        request.add("players", players);
        return request.toString();
    }

    private static boolean isLocallyBlacklisted(JsonObject player) {
        String uuid = text(player, "uuid", null);
        return uuid != null && ScamScreenerBlacklistCompat.findBlacklistedPlayerName(uuid) != null;
    }

    private static PlayerResponse requestPlayerFromApi(Target target) {
        URI endpoint = URI.create(PLAYERS_URL);
        String scheme = endpoint.getScheme();
        boolean secure = "https".equalsIgnoreCase(scheme);
        if (!secure && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("Unsupported Player API scheme: " + scheme);
        }

        String host = endpoint.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Player API endpoint has no host.");
        }

        int port = endpoint.getPort() >= 0 ? endpoint.getPort() : (secure ? 443 : 80);
        String path = endpoint.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (endpoint.getRawQuery() != null && !endpoint.getRawQuery().isEmpty()) {
            path += "?" + endpoint.getRawQuery();
        }

        byte[] body = requestBody(target).getBytes(StandardCharsets.UTF_8);
        try (Socket socket = openSocket(host, port, secure)) {
            socket.setSoTimeout(8_000);
            OutputStream output = socket.getOutputStream();
            String request = "QUERY " + path + " HTTP/1.1\r\n"
                    + "Host: " + host + (endpoint.getPort() >= 0 ? ":" + port : "") + "\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "User-Agent: " + MarketGuard.userAgent() + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();

            InputStream input = socket.getInputStream();
            return parsePlayerResponse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (PlayerApiUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Player API request failed", exception);
        }
    }

    private static Socket openSocket(String host, int port, boolean secure) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5_000);
        if (!secure) {
            return socket;
        }
        return ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
    }

    static PlayerResponse parsePlayerResponse(String response) {
        int headerEnd = response.indexOf("\r\n\r\n");
        if (headerEnd < 0) {
            throw new PlayerApiUnavailableException("Player API response was malformed.");
        }

        int statusEnd = response.indexOf("\r\n");
        if (statusEnd < 0) {
            throw new PlayerApiUnavailableException("Player API response had no status line.");
        }
        String[] statusLine = response.substring(0, statusEnd).split(" ", 3);
        if (statusLine.length < 2) {
            throw new PlayerApiUnavailableException("Player API response had no status.");
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(statusLine[1]);
        } catch (NumberFormatException exception) {
            throw new PlayerApiUnavailableException("Player API response had an invalid status.", exception);
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new PlayerApiUnavailableException("Player API request failed with status " + statusCode, statusCode);
        }

        String headers = response.substring(0, headerEnd);
        String body = response.substring(headerEnd + 4);
        if (headers.toLowerCase(Locale.ROOT).contains("\r\ntransfer-encoding: chunked")) {
            body = decodeChunkedBody(body);
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception exception) {
            throw new PlayerApiUnavailableException("Player API response was not valid JSON.", exception);
        }
        if (!root.has("players") || !root.get("players").isJsonArray() || root.getAsJsonArray("players").isEmpty()) {
            throw new PlayerApiUnavailableException("Player API response did not contain a player result.");
        }

        JsonElement result = root.getAsJsonArray("players").get(0);
        if (!result.isJsonObject()) {
            throw new PlayerApiUnavailableException("Player API result was invalid.");
        }
        JsonObject player = result.getAsJsonObject();
        String playerStatus = text(player, "status", null);
        if (playerStatus == null) {
            throw new PlayerApiUnavailableException("Player API result contained no usable status.");
        }
        if ("unavailable".equals(playerStatus)
                && text(player, "uuid", null) == null
                && text(player, "name", null) == null
                && longValue(player, "firstJoin") == null
                && object(player, "profile") == null) {
            throw new PlayerApiUnavailableException("Player API result contained no usable player data.");
        }
        String status = text(root, "status", "ok");
        return new PlayerResponse(player, "stale".equals(status));
    }

    private static String decodeChunkedBody(String body) {
        StringBuilder decoded = new StringBuilder();
        int offset = 0;
        while (offset < body.length()) {
            int lineEnd = body.indexOf("\r\n", offset);
            if (lineEnd < 0) {
                throw new PlayerApiUnavailableException("Player API chunked response was malformed.");
            }
            String sizeLine = body.substring(offset, lineEnd);
            int extension = sizeLine.indexOf(';');
            if (extension >= 0) {
                sizeLine = sizeLine.substring(0, extension);
            }
            final int size;
            try {
                size = Integer.parseInt(sizeLine.trim(), 16);
            } catch (NumberFormatException exception) {
                throw new PlayerApiUnavailableException("Player API chunked response had an invalid chunk size.", exception);
            }
            offset = lineEnd + 2;
            if (size == 0) {
                return decoded.toString();
            }
            if (size < 0 || offset + size > body.length() || offset + size + 2 > body.length()
                    || !body.startsWith("\r\n", offset + size)) {
                throw new PlayerApiUnavailableException("Player API chunked response was malformed.");
            }
            decoded.append(body, offset, offset + size);
            offset += size + 2;
        }
        throw new PlayerApiUnavailableException("Player API chunked response was malformed.");
    }

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "trade_check")
        public static HudContent tradeCheck() {
            if (view.state() == State.HIDDEN) {
                return HudLibrary.isEditing(MarketGuard.MOD_ID) ? previewContent() : hiddenContent();
            }
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.PLAYER)) {
                return hiddenContent();
            }
            View current = view;
            return switch (current.state()) {
                case HIDDEN -> hiddenContent();
                case LOADING -> loadingContent(current.target());
                case ERROR -> errorContent(current.target());
                case READY -> playerContent(current.target(), current.player(), current.blacklisted(), current.stale());
            };
        }
    }

    private static HudContent hiddenContent() {
        return HudContent.builder()
                .line(Component.literal("Trade Check"))
                .visible(false)
                .build();
    }

    private static HudContent previewContent() {
        Map<String, Component> lines = new LinkedHashMap<>();
        for (String row : HudCustomization.rows(HudCustomization.HudId.PLAYER)) {
            lines.put(row, HudCustomization.example(HudCustomization.HudId.PLAYER, row));
        }
        lines.put("title", Component.literal(preset.title()).withStyle(ChatFormatting.AQUA));
        return buildPlayer(lines);
    }

    private static HudContent loadingContent(Target target) {
        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("title", Component.literal("Trade Check").withStyle(ChatFormatting.AQUA));
        lines.put("status", Component.literal("Loading " + target.player() + "...").withStyle(ChatFormatting.GRAY));
        return buildPlayer(lines);
    }

    static HudContent errorContent(Target target) {
        ErrorDetails details = errorDetails;
        if (details == null || !details.target().equals(target)) {
            String uuid = knownUuid(target);
            details = new ErrorDetails(
                    target,
                    uuid,
                    uuid != null && ScamScreenerBlacklistCompat.findBlacklistedPlayerName(uuid) != null
            );
            errorDetails = details;
        }

        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("title", Component.literal(preset.title()).withStyle(ChatFormatting.RED));
        lines.put("name", Component.literal(target.player()).withStyle(ChatFormatting.YELLOW));
        lines.put("status", Component.literal("Player data unavailable").withStyle(ChatFormatting.GRAY));

        String uuid = details.uuid();
        if (uuid != null) {
            lines.put("seen", Component.literal(seenSummary(uuid)).withStyle(ChatFormatting.DARK_GRAY));
            lines.put("scamscreener", Component.translatable(
                    details.blacklisted() ? "marketguard.hud.scamscreener.match" : "marketguard.hud.scamscreener.no_entry"
            ).withStyle(details.blacklisted() ? ChatFormatting.RED : ChatFormatting.GRAY));
            if (preset == Preset.PROFILE || preset == Preset.ALL) {
                lines.put("uuid", Component.literal("UUID: " + uuid).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            lines.put("scamscreener", Component.translatable(
                    FabricLoader.getInstance().isModLoaded("scamscreener")
                            ? "marketguard.hud.scamscreener.installed"
                            : "marketguard.hud.scamscreener.not_installed"
            ).withStyle(ChatFormatting.GRAY));
        }
        if (preset == Preset.COMPACT && MarketGuardConfig.isPlayerHudShowUnavailableRows()) {
            lines.put("wealth", unavailableLine("Bank: n/a | Purse: n/a"));
        } else {
            addUnavailableRows(lines, preset == Preset.PROFILE || preset == Preset.ALL);
        }
        lines.put("unavailable", Component.literal("Unavailable: Player API response").withStyle(ChatFormatting.DARK_GRAY));
        return buildPlayer(lines);
    }

    static HudContent playerContent(Target target, JsonObject player, boolean blacklisted, boolean stale) {
        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("title", Component.literal(preset.title()).withStyle(ChatFormatting.AQUA));

        String status = text(player, "status", "unavailable");
        String name = text(player, "name", text(player, "uuid", target.player()));
        lines.put("name", Component.literal(name).withStyle(status.equals("ok") ? ChatFormatting.WHITE : ChatFormatting.YELLOW));

        String seen = seenSummary(text(player, "uuid", null));
        if (seen != null) {
            lines.put("seen", Component.literal(seen).withStyle(ChatFormatting.DARK_GRAY));
        }

        Long firstJoin = longValue(player, "firstJoin");
        if (firstJoin != null && firstJoin > 0L) {
            lines.put("first_join", Component.literal("First joined: "
                    + FIRST_JOIN_FORMAT.format(Instant.ofEpochMilli(firstJoin).atZone(ZoneId.systemDefault())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        String statusMessage = statusMessage(status);
        if (statusMessage != null) {
            lines.put("status", Component.literal(statusMessage).withStyle(ChatFormatting.GRAY));
        }

        JsonObject profile = object(player, "profile");
        if (preset == Preset.COMPACT) {
            addWealthSummary(lines, profile);
            addSafetyAndDataState(lines, player, blacklisted, stale);
            if (MarketGuardConfig.isPlayerHudShowUnavailableRows()) {
                lines.putIfAbsent("wealth", unavailableLine("Bank: n/a | Purse: n/a"));
            }
            return buildPlayer(lines);
        }

        if (profile != null) {
            lines.put("profile", Component.literal("Profile: " + text(profile, "name", "Unnamed profile")).withStyle(ChatFormatting.GRAY));
            JsonObject wealth = object(profile, "wealth");
            if (wealth != null) {
                String bank = coins(wealth, "bank");
                String purse = coins(wealth, "purse");
                if (bank != null || purse != null) {
                    lines.put("wealth", Component.literal("Bank: " + valueOrUnknown(bank) + " | Purse: " + valueOrUnknown(purse)).withStyle(ChatFormatting.GOLD));
                }
                addItems(lines, "Armor", array(wealth, "armor"));
                addItems(lines, "Equipment", array(wealth, "equipment"));
            }

            String activePet = activePetSummary(object(profile, "activePet"));
            if (activePet != null) {
                lines.put("pet", Component.literal("Active pet: " + activePet).withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            String skillSummary = skills(object(profile, "skills"));
            if (skillSummary != null) {
                lines.put("skills", Component.literal(skillSummary).withStyle(ChatFormatting.GREEN));
            }
        }

        if (preset == Preset.PROFILE || preset == Preset.ALL) {
            String uuid = text(player, "uuid", null);
            if (uuid != null) {
                lines.put("uuid", Component.literal("UUID: " + uuid).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        addSafetyAndDataState(lines, player, blacklisted, stale);
        addUnavailableRows(lines, preset == Preset.PROFILE || preset == Preset.ALL);
        return buildPlayer(lines);
    }

    private static HudContent buildPlayer(Map<String, Component> lines) {
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.PLAYER)) {
            Component line = lines.get(id);
            if (line != null) { content.line(line); added = true; }
        }
        return added ? content.build() : HudContent.builder().line(Component.literal("Trade Check")).visible(false).build();
    }

    private static void addWealthSummary(Map<String, Component> lines, JsonObject profile) {
        JsonObject wealth = object(profile, "wealth");
        if (wealth == null) {
            return;
        }
        String bank = coins(wealth, "bank");
        String purse = coins(wealth, "purse");
        if (bank != null || purse != null) {
            lines.put("wealth", Component.literal("Bank: " + valueOrUnknown(bank) + " | Purse: " + valueOrUnknown(purse)).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void addUnavailableRows(Map<String, Component> lines, boolean includeUuid) {
        if (!MarketGuardConfig.isPlayerHudShowUnavailableRows()) {
            return;
        }
        lines.putIfAbsent("first_join", unavailableLine("First joined: n/a"));
        lines.putIfAbsent("profile", unavailableLine("Profile: n/a"));
        lines.putIfAbsent("wealth", unavailableLine("Bank: n/a | Purse: n/a"));
        lines.putIfAbsent("armor", unavailableLine("Armor: n/a"));
        lines.putIfAbsent("equipment", unavailableLine("Equipment: n/a"));
        lines.putIfAbsent("pet", unavailableLine("Active pet: n/a"));
        lines.putIfAbsent("skills", unavailableLine("Skills: n/a"));
        if (includeUuid) {
            lines.putIfAbsent("uuid", unavailableLine("UUID: n/a"));
        }
    }

    private static Component unavailableLine(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static void addSafetyAndDataState(Map<String, Component> lines, JsonObject player, boolean blacklisted, boolean stale) {
        lines.put("scamscreener", Component.translatable(
                blacklisted ? "marketguard.hud.scamscreener.match" : "marketguard.hud.scamscreener.no_entry"
        ).withStyle(blacklisted ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (stale) {
            lines.put("data", Component.literal("Data: stale cache").withStyle(ChatFormatting.YELLOW));
        }
        String quality = dataQuality(player);
        if (quality != null) {
            lines.put("data", Component.literal("Data: " + quality).withStyle(ChatFormatting.DARK_GRAY));
        }
        String unavailable = unavailableFields(player);
        if (unavailable != null) {
            lines.put("unavailable", Component.literal("Unavailable: " + unavailable).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    static String dataQuality(JsonObject player) {
        String source = switch (text(player, "source", "")) {
            case "hypixel" -> "Hypixel API";
            case "mojang" -> "Mojang API";
            default -> null;
        };
        if (source == null) {
            return null;
        }

        Long fetchedAt = longValue(player, "fetchedAt");
        if (fetchedAt == null || fetchedAt <= 0L) {
            return source;
        }
        return source + " • " + FETCHED_AT_FORMAT.format(Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()));
    }

    static String statusMessage(String status) {
        return switch (status) {
            case "ok" -> null;
            case "partial" -> "Player data is partial";
            case "not_found" -> "Player not found";
            case "profile_not_found" -> "SkyBlock profile not found";
            case "profile_unavailable" -> "SkyBlock profile unavailable";
            case "unavailable" -> "Player data unavailable";
            default -> "Player data unavailable";
        };
    }

    static String seenSummary(String uuid) {
        return uuid == null ? null : EncounterTracker.timesSeen(uuid) + " times seen";
    }

    private static String knownUuid(Target target) {
        String resolved = RESOLVED_UUIDS.get(normalizePlayerKey(target.player()));
        if (resolved != null) {
            return resolved;
        }

        String compact = target.player().replace("-", "");
        if (compact.matches("(?i)[0-9a-f]{32}")) {
            return compact.toLowerCase(java.util.Locale.ROOT);
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getConnection() != null) {
            for (var playerInfo : client.getConnection().getListedOnlinePlayers()) {
                if (playerInfo.getProfile().name().equalsIgnoreCase(target.player())
                        && playerInfo.getProfile().id() != null) {
                    String uuid = playerInfo.getProfile().id().toString().replace("-", "");
                    RESOLVED_UUIDS.put(normalizePlayerKey(target.player()), uuid);
                    return uuid;
                }
            }
        }

        return null;
    }

    private static void addItems(Map<String, Component> lines, String label, JsonArray items) {
        String summary = itemSummary(items);
        if (summary != null) {
            lines.put(label.equals("Armor") ? "armor" : "equipment", Component.literal(label + ": " + summary).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String itemSummary(JsonArray items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        List<String> names = new ArrayList<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            String name = text(element.getAsJsonObject(), "name", null);
            if (name != null) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        if (names.size() > 2) {
            return String.join(", ", names.subList(0, 2)) + " +" + (names.size() - 2);
        }
        return String.join(", ", names);
    }

    static String activePetSummary(JsonObject pet) {
        if (pet == null) {
            return null;
        }
        String type = text(pet, "type", null);
        if (type == null) {
            return null;
        }
        StringBuilder summary = new StringBuilder();
        String tier = text(pet, "tier", null);
        if (tier != null) {
            summary.append(displayName(tier)).append(' ');
        }
        summary.append(displayName(type));

        String heldItem = text(pet, "heldItem", null);
        if (heldItem != null) {
            summary.append(" | ").append(displayName(heldItem));
        }
        return summary.toString();
    }

    private static String displayName(String value) {
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("_");
        List<String> display = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                display.add(word.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + word.substring(1));
            }
        }
        return display.isEmpty() ? value : String.join(" ", display);
    }

    private static String skills(JsonObject skills) {
        if (skills == null) {
            return null;
        }

        List<String> values = new ArrayList<>();
        for (String skill : List.of("farming", "mining", "combat")) {
            JsonObject value = object(skills, skill);
            if (value != null && value.has("level")) {
                values.add(skill.substring(0, 1).toUpperCase() + skill.substring(1) + " " + text(value, "level", "?"));
            }
        }
        return values.isEmpty() ? null : "Skills: " + String.join(" | ", values);
    }

    private static String unavailableFields(JsonObject player) {
        JsonArray unavailable = array(player, "unavailableFields");
        if (unavailable == null || unavailable.isEmpty()) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        for (JsonElement element : unavailable) {
            if (element.isJsonPrimitive()) {
                fields.add(element.getAsString());
            }
        }
        return fields.isEmpty() ? null : String.join(", ", fields);
    }

    private static String coins(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return CoinFormat.format(object.get(key).getAsDouble());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String valueOrUnknown(String value) {
        return value == null ? (MarketGuardConfig.isPlayerHudShowUnavailableRows() ? "n/a" : "?") : value;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonArray() ? parent.getAsJsonArray(key) : null;
    }

    private static String text(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String value = object.get(key).getAsString();
            return value == null || value.isBlank() ? fallback : value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Long longValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static void resetForTests() {
        CACHED_PLAYERS.clear();
        RESOLVED_UUIDS.clear();
        PLAYER_REQUESTS.clear();
        playerRequester = PlayerHud::requestPlayerFromApi;
        errorDetails = null;
        view = View.hidden();
        preset = Preset.TRADE;
        MarketGuardConfig.setPlayerHudShowUnavailableRows(false);
    }

    static void setCachedPlayerForTests(String player, String profileId, JsonObject playerData, boolean apiStale, long fetchedAt) {
        String uuid = text(playerData, "uuid", null);
        if (uuid == null) {
            throw new IllegalArgumentException("player data needs a uuid");
        }
        RESOLVED_UUIDS.put(normalizePlayerKey(player), uuid);
        CACHED_PLAYERS.put(new CacheKey(uuid, normalizeProfileId(profileId)), new CachedPlayer(playerData, false, apiStale, fetchedAt));
    }

    static void setPlayerRequesterForTests(Function<Target, PlayerResponse> requester) {
        playerRequester = requester;
    }

    private enum State {
        HIDDEN,
        LOADING,
        READY,
        ERROR
    }

    private enum Preset {
        TRADE("Trade Check"),
        COMPACT("Compact"),
        PROFILE("Profile"),
        ALL("All");

        private final String title;

        Preset(String title) {
            this.title = title;
        }

        private String title() {
            return title;
        }

        private static Preset fromConfig(String value) {
            return switch (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) {
                case "trade" -> TRADE;
                case "compact" -> COMPACT;
                case "profile" -> PROFILE;
                case "all" -> ALL;
                default -> null;
            };
        }
    }

    record Target(String player, String profileId) {}

    private record CacheKey(String uuid, String profileId) {}

    private record CachedPlayer(JsonObject player, boolean blacklisted, boolean stale, long fetchedAt) {}

    private record ErrorDetails(Target target, String uuid, boolean blacklisted) {}

    record PlayerResponse(JsonObject player, boolean stale) {}

    private record RequestKey(String player, String profileId) {
        private static RequestKey from(Target target) {
            return new RequestKey(normalizePlayerKey(target.player()), target.profileId());
        }
    }

    private record View(State state, Target target, JsonObject player, boolean blacklisted, boolean stale) {
        private static View hidden() {
            return new View(State.HIDDEN, null, null, false, false);
        }

        private static View loading(Target target) {
            return new View(State.LOADING, target, null, false, false);
        }

        private static View ready(Target target, JsonObject player, boolean blacklisted, boolean stale) {
            return new View(State.READY, target, player, blacklisted, stale);
        }

        private static View error(Target target) {
            return new View(State.ERROR, target, null, false, false);
        }
    }
}
