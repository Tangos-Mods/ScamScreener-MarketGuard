package eu.tango.scamscreener.marketguard.playerhud;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerFinanceDataTest {
    @AfterEach
    void reset() {
        PlayerFinanceData.resetForTests();
    }

    @Test
    void parsesTheNormalizedFinanceAndMuseumContract() {
        PlayerFinanceData.Response response = PlayerFinanceData.parseResponse(200, """
                {
                  "status":"partial",
                  "stale":true,
                  "fetchedAt":1715478978620,
                  "playerUuid":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "profile":{
                    "id":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    "name":"Apple",
                    "selected":true,
                    "finance":{"bank":100000000,"purse":5000000,"museumValue":25000000,"knownTotal":130000000},
                    "museum":{
                      "value":25000000,
                      "appraisal":true,
                      "donatedIds":["HYPERION","TERMINATOR"],
                      "donatedCount":2,
                      "specialIds":["DCTR_SPACE_HELM"],
                      "specialCount":1
                    }
                  },
                  "unavailableFields":["profile.museum.appraisal"]
                }
                """);

        assertEquals("partial", response.status());
        assertTrue(response.stale());
        assertEquals(130_000_000.0, response.profile().finance().knownTotal());
        assertTrue(response.profile().museum().appraisal());
        assertEquals(List.of("HYPERION", "TERMINATOR"), response.profile().museum().donatedIds());
        assertEquals(1, response.profile().museum().specialCount());
        assertTrue(response.fieldAvailable("museum.value"));
        assertFalse(response.fieldAvailable("museum.appraisal"));
    }

    @Test
    void normalizesIdsAndCoalescesConcurrentRequests() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<PlayerFinanceData.Response> pending = new CompletableFuture<>();
        PlayerFinanceData.setRequesterForTests(key -> {
            calls.incrementAndGet();
            assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", key.playerUuid());
            assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", key.profileId());
            return pending;
        });

        CompletableFuture<PlayerFinanceData.LookupResult> first = PlayerFinanceData.request(
                "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        );
        CompletableFuture<PlayerFinanceData.LookupResult> second = PlayerFinanceData.request(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        );

        assertSame(first, second);
        assertEquals(1, calls.get());
        assertTrue(PlayerFinanceData.lookupCached(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        ).loading());

        pending.complete(response("ok", List.of()));
        assertTrue(first.join().hasValue());
        assertFalse(first.join().stale());
    }

    private static PlayerFinanceData.Response response(String status, List<String> unavailableFields) {
        return new PlayerFinanceData.Response(
                status,
                false,
                1715478978620L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new PlayerFinanceData.Profile(
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "Apple",
                        true,
                        new PlayerFinanceData.Finance(1.0, 2.0, 3.0, 6.0),
                        null
                ),
                unavailableFields
        );
    }
}
