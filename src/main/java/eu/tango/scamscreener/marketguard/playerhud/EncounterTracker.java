package eu.tango.scamscreener.marketguard.playerhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.tango.scamscreener.marketguard.MarketGuard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EncounterTracker {
    private static final Object LOCK = new Object();
    private static final long LOCRAW_INTERVAL_MS = 10_000L;
    private static final long LOCRAW_RESPONSE_TIMEOUT_MS = 5_000L;
    private static final Gson GSON = new GsonBuilder().create();

    private static Path storePath = defaultPath();
    private static Map<String, Integer> encounters = new HashMap<>();
    private static boolean databaseReady;
    private static long lastLocrawRequestAt;
    private static long awaitingLocrawUntil;

    private EncounterTracker() {}

    public static void initialize() {
        synchronized (LOCK) {
            load(storePath);
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> !handleLocrawResponse(message.getString(), overlay, Minecraft.getInstance()));
        ClientTickEvents.END_CLIENT_TICK.register(EncounterTracker::requestLocrawIfDue);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEphemeralState());
    }

    public static int timesSeen(String uuid) {
        String key = normalizeUuid(uuid);
        if (key == null) {
            return 0;
        }

        synchronized (LOCK) {
            return encounters.getOrDefault(key, 0);
        }
    }

    static void observeLobbyForTests(String lobby, String localUuid, List<PlayerIdentity> players) {
        observeLobby(lobby, localUuid, players);
    }

    static void setStorePathForTests(Path path) {
        synchronized (LOCK) {
            storePath = path;
            encounters = new HashMap<>();
            databaseReady = false;
        }
    }

    static void reloadForTests() {
        synchronized (LOCK) {
            load(storePath);
        }
    }

    static void resetForTests() {
        synchronized (LOCK) {
            storePath = defaultPath();
            encounters = new HashMap<>();
            databaseReady = false;
            lastLocrawRequestAt = 0L;
            awaitingLocrawUntil = 0L;
        }
    }

    private static void requestLocrawIfDue(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null || !isHypixelServer(client.getCurrentServer())) {
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (now - lastLocrawRequestAt < LOCRAW_INTERVAL_MS) {
                return;
            }
            lastLocrawRequestAt = now;
            awaitingLocrawUntil = now + LOCRAW_RESPONSE_TIMEOUT_MS;
        }
        client.getConnection().sendCommand("locraw");
    }

    private static boolean handleLocrawResponse(String message, boolean overlay, Minecraft client) {
        if (overlay) {
            return false;
        }

        synchronized (LOCK) {
            if (System.currentTimeMillis() > awaitingLocrawUntil) {
                return false;
            }
        }

        JsonObject response;
        try {
            response = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception ignored) {
            return false;
        }
        synchronized (LOCK) {
            awaitingLocrawUntil = 0L;
        }
        if (!"SKYBLOCK".equalsIgnoreCase(text(response, "gametype")) || client == null) {
            return true;
        }

        String lobby = text(response, "server");
        if (lobby == null || lobby.isBlank() || client.player == null || client.getConnection() == null) {
            return true;
        }

        List<PlayerIdentity> players = new ArrayList<>();
        for (PlayerInfo player : client.getConnection().getListedOnlinePlayers()) {
            if (player.getProfile().id() != null) {
                players.add(new PlayerIdentity(player.getProfile().id().toString()));
            }
        }
        observeLobby(lobby, client.player.getUUID().toString(), players);
        return true;
    }

    private static void observeLobby(String lobby, String localUuid, List<PlayerIdentity> players) {
        String normalizedLobby = lobby == null ? "" : lobby.trim();
        String localPlayer = normalizeUuid(localUuid);
        if (normalizedLobby.isEmpty() || localPlayer == null || players == null) {
            return;
        }

        synchronized (LOCK) {
            if (!ensureDatabase()) {
                return;
            }

            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                try {
                    boolean changed = false;
                    List<String> newlySeen = new ArrayList<>();
                    if (!normalizedLobby.equals(currentLobby(connection))) {
                        try (Statement statement = connection.createStatement()) {
                            statement.executeUpdate("DELETE FROM encounter_lobby_players");
                        }
                        setCurrentLobby(connection, normalizedLobby);
                        changed = true;
                    }

                    for (PlayerIdentity player : players) {
                        String uuid = normalizeUuid(player.uuid());
                        if (uuid == null || uuid.equals(localPlayer) || !markPlayerInLobby(connection, uuid)) {
                            continue;
                        }
                        incrementEncounter(connection, uuid);
                        newlySeen.add(uuid);
                        changed = true;
                    }
                    if (changed) {
                        connection.commit();
                        for (String uuid : newlySeen) {
                            encounters.merge(uuid, 1, Integer::sum);
                        }
                    } else {
                        connection.rollback();
                    }
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (Exception exception) {
                MarketGuard.LOGGER.warn("Failed to record lobby encounter in {}", storePath, exception);
            }
        }
    }

    private static boolean ensureDatabase() {
        if (databaseReady) {
            return true;
        }

        boolean migrateLegacyJson = Files.notExists(storePath) && Files.exists(legacyJsonPath(storePath));
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS encounters (uuid TEXT PRIMARY KEY NOT NULL, times_seen INTEGER NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS encounter_lobby_players (uuid TEXT PRIMARY KEY NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS encounter_state (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
            }
            if (migrateLegacyJson) {
                migrateLegacyJson();
            }
            databaseReady = true;
            return true;
        } catch (Exception exception) {
            MarketGuard.LOGGER.warn("Failed to initialize encounter database at {}", storePath, exception);
            return false;
        }
    }

    private static void load(Path path) {
        storePath = path;
        encounters = new HashMap<>();
        databaseReady = false;
        if (!ensureDatabase()) {
            return;
        }

        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT uuid, times_seen FROM encounters")) {
            while (result.next()) {
                encounters.put(result.getString("uuid"), result.getInt("times_seen"));
            }
        } catch (SQLException exception) {
            MarketGuard.LOGGER.warn("Failed to load encounter history from {}", storePath, exception);
        }
    }

    private static void migrateLegacyJson() {
        Path legacyPath = legacyJsonPath(storePath);
        try (Reader reader = Files.newBufferedReader(legacyPath)) {
            LegacyEncounterState legacy = GSON.fromJson(reader, LegacyEncounterState.class);
            if (legacy == null) {
                return;
            }

            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (legacy.currentLobby != null && !legacy.currentLobby.isBlank()) {
                        setCurrentLobby(connection, legacy.currentLobby);
                    }
                    if (legacy.currentLobbyPlayers != null) {
                        for (String uuid : legacy.currentLobbyPlayers) {
                            String normalizedUuid = normalizeUuid(uuid);
                            if (normalizedUuid != null) {
                                markPlayerInLobby(connection, normalizedUuid);
                            }
                        }
                    }
                    if (legacy.encounters != null) {
                        for (Map.Entry<String, LegacyEncounter> entry : legacy.encounters.entrySet()) {
                            String uuid = normalizeUuid(entry.getKey());
                            if (uuid != null && entry.getValue() != null && entry.getValue().timesSeen > 0) {
                                setEncounterCount(connection, uuid, entry.getValue().timesSeen);
                            }
                        }
                    }
                    connection.commit();
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        } catch (Exception exception) {
            MarketGuard.LOGGER.warn("Failed to migrate legacy encounter history from {}", legacyPath, exception);
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath());
    }

    private static String currentLobby(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM encounter_state WHERE key = 'current_lobby'")) {
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void setCurrentLobby(Connection connection, String lobby) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO encounter_state(key, value) VALUES('current_lobby', ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, lobby);
            statement.executeUpdate();
        }
    }

    private static boolean markPlayerInLobby(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO encounter_lobby_players(uuid) VALUES(?)")) {
            statement.setString(1, uuid);
            return statement.executeUpdate() > 0;
        }
    }

    private static void incrementEncounter(Connection connection, String uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO encounters(uuid, times_seen) VALUES(?, 1) ON CONFLICT(uuid) DO UPDATE SET times_seen = times_seen + 1")) {
            statement.setString(1, uuid);
            statement.executeUpdate();
        }
    }

    private static void setEncounterCount(Connection connection, String uuid, int count) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO encounters(uuid, times_seen) VALUES(?, ?) ON CONFLICT(uuid) DO UPDATE SET times_seen = excluded.times_seen")) {
            statement.setString(1, uuid);
            statement.setInt(2, count);
            statement.executeUpdate();
        }
    }

    private static void clearEphemeralState() {
        synchronized (LOCK) {
            lastLocrawRequestAt = 0L;
            awaitingLocrawUntil = 0L;
        }
    }

    private static boolean isHypixelServer(ServerData server) {
        if (server == null || server.ip == null) {
            return false;
        }
        String host = server.ip.toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static String normalizeUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            String compact = value.trim().replace("-", "");
            if (compact.length() != 32) {
                return null;
            }
            return UUID.fromString(compact.substring(0, 8)
                    + "-" + compact.substring(8, 12)
                    + "-" + compact.substring(12, 16)
                    + "-" + compact.substring(16, 20)
                    + "-" + compact.substring(20)).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Path defaultPath() {
        try {
            return FabricLoader.getInstance().getConfigDir()
                    .resolve("scamscreener_marketguard")
                    .resolve("encounters.db");
        } catch (Exception ignored) {
            return Path.of("encounters.db");
        }
    }

    private static Path legacyJsonPath(Path databasePath) {
        Path parent = databasePath.getParent();
        return parent == null ? Path.of("encounters.json") : parent.resolve("encounters.json");
    }

    record PlayerIdentity(String uuid) {}

    private static final class LegacyEncounterState {
        String currentLobby;
        Set<String> currentLobbyPlayers = new LinkedHashSet<>();
        Map<String, LegacyEncounter> encounters = new LinkedHashMap<>();
    }

    private static final class LegacyEncounter {
        int timesSeen;
    }
}
