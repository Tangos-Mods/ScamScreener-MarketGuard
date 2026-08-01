package eu.tango.scamscreener.marketguard.hud;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.api.MarketGuardApi;
import eu.tango.scamscreener.marketguard.api.PlayerApiUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerHudApiTest {
    @AfterEach
    void resetPlayerCache() {
        PlayerHud.resetForTests();
    }

    @Test
    void exposesTheFreshPlayerCacheWithoutARequest() {
        PlayerHud.setCachedPlayerForTests("Pankraz01", null, player("Pankraz01"), false, System.currentTimeMillis());

        MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> result =
                MarketGuardApi.lookupCachedPlayer("pankraz01", null);

        assertTrue(result.hasValue());
        assertEquals("Pankraz01", result.value().name());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertFalse(result.refreshFailed());

        assertTrue(MarketGuardApi.lookupCachedPlayer("not-a-minecraft-uuid", null).hasValue());
    }

    @Test
    void deduplicatesConcurrentPlayerRequestsAndCachesTheResult() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PlayerHud.setPlayerRequesterForTests(target -> {
            requests.incrementAndGet();
            started.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return new PlayerHud.PlayerResponse(player("Pankraz01"), false);
        });

        CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>> first =
                MarketGuardApi.requestPlayer("Pankraz01", null);
        assertTrue(started.await(5, TimeUnit.SECONDS));
        CompletableFuture<MarketGuardApi.CachedValue<MarketGuardApi.PlayerData>> second =
                MarketGuardApi.requestPlayer("Pankraz01", null);

        assertSame(first, second);
        assertEquals(1, requests.get());
        release.countDown();

        MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> result = first.get(5, TimeUnit.SECONDS);
        assertTrue(result.hasValue());
        assertEquals("Pankraz01", result.value().name());
        assertTrue(MarketGuardApi.lookupCachedPlayer("Pankraz01", null).hasValue());
    }

    @Test
    void returnsCacheFailureStatusInsteadOfPropagatingAPlayerRequestError() throws Exception {
        PlayerHud.setPlayerRequesterForTests(target -> {
            throw new IllegalStateException("offline");
        });

        MarketGuardApi.CachedValue<MarketGuardApi.PlayerData> result =
                MarketGuardApi.requestPlayer("Pankraz01", null).get(5, TimeUnit.SECONDS);

        assertFalse(result.hasValue());
        assertFalse(result.stale());
        assertFalse(result.loading());
        assertTrue(result.refreshFailed());
    }

    @Test
    void propagatesTypedErrorWhenReachableApiIsUnavailable() {
        PlayerHud.setPlayerRequesterForTests(target -> {
            throw new PlayerApiUnavailableException("Player endpoint unavailable", 503);
        });

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> MarketGuardApi.requestPlayer("Pankraz01", null).get(5, TimeUnit.SECONDS));

        PlayerApiUnavailableException cause = assertInstanceOf(PlayerApiUnavailableException.class, error.getCause());
        assertEquals(503, cause.statusCode());
    }

    @Test
    void rejectsSuccessfulResponsesWithoutAUsablePlayerResult() {
        PlayerApiUnavailableException error = assertThrows(PlayerApiUnavailableException.class,
                () -> PlayerHud.parsePlayerResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"status\":\"ok\",\"players\":[{}]}"));

        assertEquals("Player API result contained no usable status.", error.getMessage());
    }

    @Test
    void rejectsUnavailableResultsWithoutAnyPlayerData() {
        PlayerApiUnavailableException error = assertThrows(PlayerApiUnavailableException.class,
                () -> PlayerHud.parsePlayerResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"status\":\"ok\",\"players\":[{\"status\":\"unavailable\"}]}"));

        assertEquals("Player API result contained no usable player data.", error.getMessage());
    }

    @Test
    void keepsUnavailableResultsWhenTheyStillContainPlayerData() {
        PlayerHud.PlayerResponse response = PlayerHud.parsePlayerResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"status\":\"ok\",\"players\":[{\"status\":\"unavailable\",\"name\":\"Pankraz01\"}]}"
        );

        assertEquals("Pankraz01", response.player().get("name").getAsString());
    }

    @Test
    void decodesChunkedPlayerResponses() {
        PlayerHud.PlayerResponse response = PlayerHud.parsePlayerResponse(
                "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                        + "40\r\n{\"status\":\"ok\",\"players\":[{\"status\":\"partial\",\"name\":\"Pan_05\"}]}\r\n0\r\n\r\n"
        );

        assertEquals("Pan_05", response.player().get("name").getAsString());
    }

    private static JsonObject player(String name) {
        JsonObject player = new JsonObject();
        player.addProperty("uuid", "not-a-minecraft-uuid");
        player.addProperty("name", name);
        player.addProperty("status", "ok");
        return player;
    }
}
