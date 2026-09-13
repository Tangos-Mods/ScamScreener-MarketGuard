package eu.tango.scamscreener.marketguard.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.ApiEndpoint;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.MarketGuardConfig.PlayerHudPreset;
import eu.tango.scamscreener.marketguard.api.MarketGuardApi;
import eu.tango.scamscreener.marketguard.api.PlayerApiUnavailableException;
import eu.tango.scamscreener.marketguard.auction.AuctionReferencePrice;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.playerhud.EncounterTracker;
import eu.tango.scamscreener.marketguard.playerhud.PlayerFinanceData;
import eu.tango.scamscreener.marketguard.playerhud.VisibleProfileValue;
import eu.tango.scamscreener.marketguard.util.CoinFormat;
import net.fabricmc.loader.api.FabricLoader;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PlayerHud {
    private static final String PLAYERS_URL = ApiEndpoint.url("/api/v1/players");
    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final DateTimeFormatter FETCHED_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FIRST_JOIN_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final AtomicLong REQUEST_ID = new AtomicLong();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static volatile View view = View.hidden();
    private static volatile ErrorDetails errorDetails;
    private static final ConcurrentHashMap<CacheKey, CachedPlayer> CACHED_PLAYERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> RESOLVED_UUIDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<RequestKey, CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>>> PLAYER_REQUESTS = new ConcurrentHashMap<>();
    private static volatile Function<Target, PlayerResponse> playerRequester = PlayerHud::requestPlayerFromApi;
    private static volatile BiFunction<String, String, PlayerFinanceData.LookupResult> financeLookup = PlayerFinanceData::lookupCached;

    private PlayerHud() {}

    public static void initialize() {
        MarketGuard.LOGGER.info("Player HUD API endpoint: {}", ApiEndpoint.baseUrl());
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, InputConstants.KEY_F8, Widgets.class, true);
    }

    public static void show(String player, String profileId) {
        Target target = target(player, profileId);
        if (target == null) {
            return;
        }

        long requestId = REQUEST_ID.incrementAndGet();
        errorDetails = null;
        view = View.loading(target);
        refreshPriceCachesIfNeeded();
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
            requestPlayerFinance(playerData.raw());
        });
    }

    private static void requestPlayerFinance(JsonObject player) {
        if (MarketGuardConfig.playerHudPreset == PlayerHudPreset.trade) {
            return;
        }
        JsonObject profile = object(player, "profile");
        String playerUuid = text(player, "uuid", null);
        String profileId = text(profile, "id", null);
        if (playerUuid != null && profileId != null) {
            PlayerFinanceData.request(playerUuid, profileId);
        }
    }

    private static void refreshPriceCachesIfNeeded() {
        if (MarketGuardConfig.playerHudPreset == PlayerHudPreset.trade) {
            return;
        }
        BazaarData.refreshAsyncIfNeeded();
        LowestBinData.refreshAsyncIfNeeded();
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PLAYERS_URL))
                .header("Content-Type", "application/json")
                .header("User-Agent", MarketGuard.userAgent())
                .timeout(Duration.ofSeconds(8))
                .method("QUERY", HttpRequest.BodyPublishers.ofString(requestBody(target)))
                .build();
        HttpResponse<String> response;
        try {
            response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Player API request failed", exception);
        }
        return parsePlayerResponse(response.statusCode(), response.body());
    }

    static PlayerResponse parsePlayerResponse(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new PlayerApiUnavailableException("Player API request failed with status " + statusCode, statusCode);
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
        return buildPlayer(lines);
    }

    private static HudContent loadingContent(Target target) {
        Map<String, Component> lines = new LinkedHashMap<>();
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
        lines.put("name", Component.literal(target.player()).withStyle(ChatFormatting.YELLOW));
        lines.put("status", Component.literal("Player data unavailable").withStyle(ChatFormatting.GRAY));
        if (details.uuid() != null) {
            lines.put("seen", seenLine(details.uuid()));
            lines.put("uuid", Component.literal("UUID: " + details.uuid()).withStyle(ChatFormatting.DARK_GRAY));
            addScamScreener(lines, details.blacklisted());
        }
        addUnavailableRows(lines);
        return buildPlayer(lines);
    }

    static HudContent playerContent(Target target, JsonObject player, boolean blacklisted, boolean stale) {
        Map<String, Component> lines = new LinkedHashMap<>();
        String uuid = text(player, "uuid", null);
        lines.put("name", Component.literal(text(player, "name", target.player())).withStyle(ChatFormatting.WHITE));
        if (uuid != null) {
            lines.put("seen", seenLine(uuid));
            lines.put("uuid", Component.literal("UUID: " + uuid).withStyle(ChatFormatting.DARK_GRAY));
        }
        addScamScreener(lines, blacklisted);

        String statusMessage = statusMessage(text(player, "status", "unavailable"));
        if (statusMessage != null) {
            lines.put("status", Component.literal(statusMessage).withStyle(ChatFormatting.GRAY));
        }

        Long firstJoin = longValue(player, "firstJoin");
        if (firstJoin != null && firstJoin > 0L) {
            lines.put("first_join", Component.literal("First joined: "
                    + FIRST_JOIN_FORMAT.format(Instant.ofEpochMilli(firstJoin).atZone(ZoneId.systemDefault())))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        JsonObject profile = object(player, "profile");
        if (profile != null) {
            String profileSummary = profileSummary(profile);
            if (profileSummary != null) {
                lines.put("profile", Component.literal(profileSummary).withStyle(ChatFormatting.GRAY));
            }
            JsonObject wealth = object(profile, "wealth");
            addWealth(lines, wealth);
            addItems(lines, "Armor", array(wealth, "armor"));
            addItems(lines, "Equipment", array(wealth, "equipment"));

            String activePet = activePetSummary(object(profile, "activePet"));
            if (activePet != null) {
                lines.put("pet", Component.literal("Active pet: " + activePet).withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            String skillSummary = skills(object(profile, "skills"));
            if (skillSummary != null) {
                lines.put("skills", Component.literal(skillSummary).withStyle(ChatFormatting.GREEN));
            }

            PlayerFinanceData.LookupResult finance = finance(player, profile);
            addNetWorth(lines, wealth, finance);
            addMuseum(lines, finance);
        }

        if (stale) {
            lines.put("data", Component.literal("Data may be outdated").withStyle(ChatFormatting.YELLOW));
        } else {
            String updated = updatedAt(player);
            if (updated != null) {
                lines.put("data", Component.literal(updated).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        addUnavailableRows(lines);
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

    private static void addWealth(Map<String, Component> lines, JsonObject wealth) {
        Double bank = number(wealth, "bank");
        Double purse = number(wealth, "purse");
        String summary;
        if (bank != null && purse != null) {
            summary = "Bank + purse: " + CoinFormat.format(bank + purse);
        } else if (bank != null) {
            summary = "Bank: " + CoinFormat.format(bank);
        } else if (purse != null) {
            summary = "Purse: " + CoinFormat.format(purse);
        } else {
            return;
        }
        lines.put("wealth", Component.literal(summary).withStyle(ChatFormatting.GOLD));
    }

    private static PlayerFinanceData.LookupResult finance(JsonObject player, JsonObject profile) {
        String playerUuid = text(player, "uuid", null);
        String profileId = text(profile, "id", null);
        return playerUuid == null || profileId == null
                ? new PlayerFinanceData.LookupResult(null, false, false, false)
                : financeLookup.apply(playerUuid, profileId);
    }

    private static void addNetWorth(Map<String, Component> lines, JsonObject wealth, PlayerFinanceData.LookupResult lookup) {
        JsonArray armor = array(wealth, "armor");
        JsonArray equipment = array(wealth, "equipment");
        List<VisibleProfileValue.Item> visibleItems = new ArrayList<>();
        addVisibleItems(visibleItems, armor);
        addVisibleItems(visibleItems, equipment);
        PlayerFinanceData.Response response = lookup.value();
        Double financeTotal = knownFinanceTotal(response);
        VisibleProfileValue.Estimate estimate = VisibleProfileValue.estimate(
                financeTotal,
                visibleItems,
                PlayerHud::visibleItemPrice
        );

        if (financeTotal != null) {
            lines.put("profile_value", Component.literal("Est. net worth: ~" + CoinFormat.format(estimate.value()))
                    .withStyle(ChatFormatting.GOLD));
            lines.put("value_missing", Component.literal(
                    "Not included: inventory, pets" + (estimate.missingItemPrices() > 0 ? ", some gear prices" : "")
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (estimate.visibleItems() > 0) {
            lines.put("value_coverage", Component.literal(
                    "Priced gear: " + estimate.pricedItems() + "/" + estimate.visibleItems() + " items"
            ).withStyle(estimate.missingItemPrices() == 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }

        if (lookup.loading() || estimate.loading()) {
            lines.put("value_status", Component.literal("Loading finance data...").withStyle(ChatFormatting.GRAY));
        } else if (lookup.refreshFailed() || (response != null && !response.usable())) {
            lines.put("value_status", Component.literal("Finance data unavailable").withStyle(ChatFormatting.YELLOW));
        } else if (lookup.stale() || (response != null && response.stale()) || estimate.stale() || estimate.refreshFailed()) {
            lines.put("value_status", Component.literal("Some values may be outdated").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static Double knownFinanceTotal(PlayerFinanceData.Response response) {
        if (response == null || !response.usable() || response.profile().finance() == null) {
            return null;
        }
        PlayerFinanceData.Finance finance = response.profile().finance();
        if (response.fieldAvailable("finance.knownTotal") && validNumber(finance.knownTotal())) {
            return finance.knownTotal();
        }

        double total = 0.0;
        boolean known = false;
        if (response.fieldAvailable("finance.bank") && validNumber(finance.bank())) {
            total += finance.bank();
            known = true;
        }
        if (response.fieldAvailable("finance.purse") && validNumber(finance.purse())) {
            total += finance.purse();
            known = true;
        }
        if (response.fieldAvailable("finance.museumValue") && validNumber(finance.museumValue())) {
            total += finance.museumValue();
            known = true;
        }
        return known ? total : null;
    }

    private static void addMuseum(Map<String, Component> lines, PlayerFinanceData.LookupResult lookup) {
        PlayerFinanceData.Response response = lookup.value();
        PlayerFinanceData.Museum museum = response == null || !response.usable() ? null : response.profile().museum();
        if (museum == null) {
            return;
        }
        if (response.fieldAvailable("museum.value") && validNumber(museum.value())) {
            String appraised = response.fieldAvailable("museum.appraisal") && Boolean.TRUE.equals(museum.appraisal())
                    ? ", appraised"
                    : "";
            lines.put("museum", Component.literal("Museum: " + CoinFormat.format(museum.value()) + appraised)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (response.fieldAvailable("museum.donatedCount") && museum.donatedCount() != null) {
            String special = response.fieldAvailable("museum.specialCount") && museum.specialCount() != null
                    ? " (" + museum.specialCount() + " special)"
                    : "";
            lines.put("museum_items", Component.literal("Museum: " + museum.donatedCount() + " exhibits" + special)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean validNumber(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0;
    }

    private static void addVisibleItems(List<VisibleProfileValue.Item> items, JsonArray values) {
        if (values == null) {
            return;
        }
        for (JsonElement element : values) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            Long count = longValue(item, "count");
            int quantity = count != null && count > 0L && count <= Integer.MAX_VALUE ? count.intValue() : 1;
            items.add(new VisibleProfileValue.Item(text(item, "id", null), quantity));
        }
    }

    static VisibleProfileValue.ItemPrice visibleItemPrice(String itemId) {
        BazaarData.LookupResult bazaar = BazaarData.lookupProduct(itemId);
        if (bazaar.hasValue() && Double.isFinite(bazaar.value().sell()) && bazaar.value().sell() > 0.0) {
            return new VisibleProfileValue.ItemPrice(
                    bazaar.value().sell(),
                    false,
                    bazaar.stale(),
                    bazaar.loading(),
                    bazaar.refreshFailed()
            );
        }

        LowestBinData.LookupResult auction = LowestBinData.lookupPriceData(itemId);
        AuctionReferencePrice reference = AuctionReferencePrice.select(
                auction.value(),
                auction.average7d(),
                auction.average30d()
        ).orElse(null);
        if (reference != null && reference.safeForProtection()) {
            return new VisibleProfileValue.ItemPrice(
                    reference.value(),
                    false,
                    auction.stale(),
                    auction.loading(),
                    auction.refreshFailed()
            );
        }
        return new VisibleProfileValue.ItemPrice(
                null,
                reference != null,
                bazaar.stale() || auction.stale(),
                bazaar.loading() || auction.loading(),
                bazaar.refreshFailed() || auction.refreshFailed()
        );
    }

    private static String profileSummary(JsonObject profile) {
        String name = text(profile, "name", null);
        if (name != null) {
            return "Profile: " + name;
        }
        String id = text(profile, "id", null);
        return id == null ? null : "Profile ID: " + id;
    }

    private static void addUnavailableRows(Map<String, Component> lines) {
        if (!MarketGuardConfig.isPlayerHudShowUnavailableRows()) {
            return;
        }
        lines.putIfAbsent("first_join", unavailableLine("First joined: n/a"));
        lines.putIfAbsent("profile", unavailableLine("Profile: n/a"));
        lines.putIfAbsent("wealth", unavailableLine("Bank + purse: n/a"));
        lines.putIfAbsent("profile_value", unavailableLine("Est. net worth: n/a"));
        lines.putIfAbsent("value_coverage", unavailableLine("Priced gear: n/a"));
        lines.putIfAbsent("museum", unavailableLine("Museum: n/a"));
        lines.putIfAbsent("museum_items", unavailableLine("Museum exhibits: n/a"));
        lines.putIfAbsent("armor", unavailableLine("Armor: n/a"));
        lines.putIfAbsent("equipment", unavailableLine("Equipment: n/a"));
        lines.putIfAbsent("pet", unavailableLine("Active pet: n/a"));
        lines.putIfAbsent("skills", unavailableLine("Skills: n/a"));
        lines.putIfAbsent("uuid", unavailableLine("UUID: n/a"));
    }

    private static Component unavailableLine(String text) {
        return Component.literal(text).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static void addScamScreener(Map<String, Component> lines, boolean blacklisted) {
        if (FabricLoader.getInstance().isModLoaded("scamscreener")) {
            lines.put("scamscreener", Component.translatable(
                    blacklisted ? "marketguard.hud.scamscreener.match" : "marketguard.hud.scamscreener.no_entry"
            ).withStyle(blacklisted ? ChatFormatting.RED : ChatFormatting.GRAY));
        } else if (MarketGuardConfig.playerHudPreset == PlayerHudPreset.profile
                || MarketGuardConfig.playerHudPreset == PlayerHudPreset.all) {
            lines.put("scamscreener", Component.translatable("marketguard.hud.scamscreener.not_installed")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    static String updatedAt(JsonObject player) {
        Long fetchedAt = longValue(player, "fetchedAt");
        if (fetchedAt == null || fetchedAt <= 0L) {
            return null;
        }
        return "Updated " + FETCHED_AT_FORMAT.format(Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()));
    }

    static String statusMessage(String status) {
        return switch (status) {
            case "ok", "partial" -> null;
            case "not_found" -> "Player not found";
            case "profile_not_found" -> "SkyBlock profile not found";
            case "profile_unavailable" -> "SkyBlock profile unavailable";
            default -> "Player data unavailable";
        };
    }

    static String seenSummary(String uuid) {
        int times = EncounterTracker.timesSeen(uuid);
        return switch (times) {
            case 0 -> "Never seen before";
            case 1 -> "Seen once";
            default -> "Seen " + times + " times";
        };
    }

    private static Component seenLine(String uuid) {
        return Component.literal(seenSummary(uuid)).withStyle(ChatFormatting.DARK_GRAY);
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
        double levelTotal = 0.0;
        int levelCount = 0;
        for (Map.Entry<String, JsonElement> entry : skills.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            Double level = number(entry.getValue().getAsJsonObject(), "level");
            if (level != null && level >= 0.0) {
                levelTotal += level;
                levelCount++;
            }
        }
        for (String skill : List.of("farming", "mining", "combat")) {
            JsonObject value = object(skills, skill);
            if (value != null && value.has("level")) {
                values.add(skill.substring(0, 1).toUpperCase() + skill.substring(1) + " " + text(value, "level", "?"));
            }
        }
        if (levelCount > 0) {
            values.add(String.format(
                    Locale.US,
                    "Avg %.1f (%d %s)",
                    levelTotal / levelCount,
                    levelCount,
                    levelCount == 1 ? "skill" : "skills"
            ));
        }
        return values.isEmpty() ? null : "Skills: " + String.join(" | ", values);
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
        financeLookup = PlayerFinanceData::lookupCached;
        errorDetails = null;
        view = View.hidden();
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

    static void setFinanceLookupForTests(BiFunction<String, String, PlayerFinanceData.LookupResult> lookup) {
        financeLookup = lookup;
    }

    private enum State {
        HIDDEN,
        LOADING,
        READY,
        ERROR
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
