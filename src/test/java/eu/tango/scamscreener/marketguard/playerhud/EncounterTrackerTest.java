package eu.tango.scamscreener.marketguard.playerhud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EncounterTrackerTest {
    private static final String LOCAL_PLAYER = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_PLAYER = "00000000-0000-0000-0000-000000000002";

    @AfterEach
    void resetTracker() {
        EncounterTracker.resetForTests();
    }

    @Test
    void countsAPlayerOnlyOncePerLobbyAndAgainAfterChangingLobbies(@TempDir Path tempDir) {
        EncounterTracker.setStorePathForTests(tempDir.resolve("encounters.db"));
        List<EncounterTracker.PlayerIdentity> players = List.of(
                new EncounterTracker.PlayerIdentity(LOCAL_PLAYER),
                new EncounterTracker.PlayerIdentity(OTHER_PLAYER)
        );

        EncounterTracker.observeLobbyForTests("mini123A", LOCAL_PLAYER, players);
        EncounterTracker.observeLobbyForTests("mini123A", LOCAL_PLAYER, players);
        assertEquals(1, EncounterTracker.timesSeen(OTHER_PLAYER));

        EncounterTracker.observeLobbyForTests("mini456B", LOCAL_PLAYER, players);
        assertEquals(2, EncounterTracker.timesSeen(OTHER_PLAYER));
        assertEquals(2, EncounterTracker.timesSeen(OTHER_PLAYER.replace("-", "")));
    }

    @Test
    void keepsTheCurrentLobbyDeduplicationAfterReload(@TempDir Path tempDir) {
        Path path = tempDir.resolve("encounters.db");
        EncounterTracker.setStorePathForTests(path);
        List<EncounterTracker.PlayerIdentity> players = List.of(
                new EncounterTracker.PlayerIdentity(OTHER_PLAYER)
        );

        EncounterTracker.observeLobbyForTests("mini123A", LOCAL_PLAYER, players);
        EncounterTracker.reloadForTests();
        EncounterTracker.observeLobbyForTests("mini123A", LOCAL_PLAYER, players);

        assertEquals(1, EncounterTracker.timesSeen(OTHER_PLAYER));
    }

    @Test
    void storesEncounterHistoryInSqlite(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("encounters.db");
        EncounterTracker.setStorePathForTests(path);

        EncounterTracker.observeLobbyForTests("mini123A", LOCAL_PLAYER, List.of(new EncounterTracker.PlayerIdentity(OTHER_PLAYER)));

        assertEquals("SQLite format 3\u0000", new String(java.nio.file.Files.readAllBytes(path), 0, 16, StandardCharsets.US_ASCII));
    }

    @Test
    void migratesExistingJsonHistoryToSqlite(@TempDir Path tempDir) throws Exception {
        Path databasePath = tempDir.resolve("encounters.db");
        Files.writeString(tempDir.resolve("encounters.json"), """
                {
                  "currentLobby": "mini123A",
                  "currentLobbyPlayers": ["00000000-0000-0000-0000-000000000002"],
                  "encounters": {
                    "00000000-0000-0000-0000-000000000002": {"timesSeen": 3}
                  }
                }
                """);
        EncounterTracker.setStorePathForTests(databasePath);

        EncounterTracker.reloadForTests();
        EncounterTracker.observeLobbyForTests("mini123A", LOCAL_PLAYER, List.of(new EncounterTracker.PlayerIdentity(OTHER_PLAYER)));

        assertEquals(3, EncounterTracker.timesSeen(OTHER_PLAYER));
        assertEquals("SQLite format 3\u0000", new String(Files.readAllBytes(databasePath), 0, 16, StandardCharsets.US_ASCII));
    }
}
