package eu.tango.scamscreener.marketguard.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.data.BazaarData;
import eu.tango.scamscreener.marketguard.data.LowestBinData;
import eu.tango.scamscreener.marketguard.playerhud.EncounterTracker;
import eu.tango.scamscreener.marketguard.playerhud.PlayerFinanceData;
import eu.tango.tangosHudLib.api.HudContent;
import net.fabricmc.loader.api.FabricLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PlayerHudTest {
    @AfterEach
    void reset() {
        PlayerHud.resetForTests();
        MarketGuardConfig.setPlayerHudPreset("trade");
        MarketGuardConfig.playerHudRows = new ArrayList<>(List.of(
                "name", "seen", "scamscreener", "status", "wealth", "profile_value",
                "first_join", "profile", "value_coverage", "value_missing", "value_status",
                "museum", "museum_items", "armor", "equipment", "pet", "skills", "uuid", "data"
        ));
    }

    @Test
    void showsAStatusOnlyWhenThePlayerCouldNotBeResolved() {
        assertNull(PlayerHud.statusMessage("ok"));
        assertNull(PlayerHud.statusMessage("partial"));
        assertEquals("Player not found", PlayerHud.statusMessage("not_found"));
        assertEquals("SkyBlock profile not found", PlayerHud.statusMessage("profile_not_found"));
        assertEquals("SkyBlock profile unavailable", PlayerHud.statusMessage("profile_unavailable"));
        assertEquals("Player data unavailable", PlayerHud.statusMessage("unavailable"));
    }

    @Test
    void partialPlayerDataShowsNoStatusLine() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = new JsonObject();
        player.addProperty("status", "partial");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertEquals("Pankraz01", lines.getFirst());
        assertTrue(lines.stream().noneMatch(line -> line.contains("partial") || line.contains("unavailable")));
    }

    @Test
    void fallsBackToTheRequestedNameInsteadOfTheUuidWhenTheApiOmitsTheName() {
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertEquals("Pankraz01", lines.getFirst());
    }

    @Test
    void wordsTheSeenCountForFirstSingleAndRepeatedEncounters() {
        try (MockedStatic<EncounterTracker> encounters = mockStatic(EncounterTracker.class)) {
            encounters.when(() -> EncounterTracker.timesSeen("a")).thenReturn(0);
            encounters.when(() -> EncounterTracker.timesSeen("b")).thenReturn(1);
            encounters.when(() -> EncounterTracker.timesSeen("c")).thenReturn(3);

            assertEquals("Never seen before", PlayerHud.seenSummary("a"));
            assertEquals("Seen once", PlayerHud.seenSummary("b"));
            assertEquals("Seen 3 times", PlayerHud.seenSummary("c"));
        }
    }

    @Test
    void compactPresetStartsWithThePlayerAndFormatsSeenLikeTheEditorPreview() {
        MarketGuardConfig.setPlayerHudPreset("compact");
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");

        try (MockedStatic<EncounterTracker> encounters = mockStatic(EncounterTracker.class)) {
            encounters.when(() -> EncounterTracker.timesSeen("fd9347ca546f4a4c89239665caa0385c")).thenReturn(12);

            List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();

            assertEquals(List.of("Pankraz01", "Seen 12 times"), lines);
        }
    }

    @Test
    void showsTheBlacklistWarningOnlyWhenScamScreenerIsInstalled() {
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, true, false)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("marketguard.hud.scamscreener.")));

        MarketGuardConfig.setPlayerHudPreset("all");
        lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, true, false)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.contains("marketguard.hud.scamscreener.not_installed"));

        try (MockedStatic<FabricLoader> fabric = mockStatic(FabricLoader.class)) {
            FabricLoader loader = mock(FabricLoader.class);
            when(loader.isModLoaded("scamscreener")).thenReturn(true);
            fabric.when(FabricLoader::getInstance).thenReturn(loader);

            lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, true, false)
                    .lines().stream().map(line -> line.getString()).toList();
            assertTrue(lines.contains("marketguard.hud.scamscreener.match"));

            lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();
            assertTrue(lines.contains("marketguard.hud.scamscreener.no_entry"));
        }
    }

    @Test
    void showsOnlyTheUpdateTimeActuallyProvidedByTheResponse() {
        JsonObject player = new JsonObject();
        player.addProperty("fetchedAt", 1_715_478_978_620L);
        assertTrue(PlayerHud.updatedAt(player).matches("Updated \\d\\d:\\d\\d"));

        assertNull(PlayerHud.updatedAt(new JsonObject()));
    }

    @Test
    void replacesTheUpdateTimeWithAWarningForStaleData() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");
        player.addProperty("fetchedAt", 1_715_478_978_620L);

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Updated ")));

        lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, true)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.contains("Data may be outdated"));
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("Updated ")));
    }

    @Test
    void showsFailedFinanceRefreshWithoutANetWorthLine() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(null, false, false, true));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Finance data unavailable"));
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("Est. net worth") || line.startsWith("Not included")));
    }

    @Test
    void showsLoadingAndOutdatedFinanceDataAsWarnings() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(null, false, true, false));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.contains("Loading finance data..."));

        PlayerFinanceData.Museum museum = new PlayerFinanceData.Museum(25_000_000.0, null, 2, 0);
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("ok", 11_000_000.0, museum, List.of()),
                true,
                false,
                false
        ));

        lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.contains("Est. net worth: ~11,000,000"));
        assertTrue(lines.contains("Museum: 25,000,000"));
        assertTrue(lines.contains("Museum: 2 exhibits (0 special)"));
        assertTrue(lines.contains("Some values may be outdated"));

        PlayerFinanceData.Response fresh = financeResponse("ok", 11_000_000.0, museum, List.of());
        PlayerFinanceData.Response serverStale = new PlayerFinanceData.Response(
                fresh.status(), true, fresh.playerUuid(), fresh.profile(), fresh.unavailableFields());
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(serverStale, false, false, false));

        lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();
        assertTrue(lines.contains("Some values may be outdated"));
    }

    @Test
    void showsActivePetWithoutInventingAnActiveWeapon() {
        JsonObject pet = new JsonObject();
        pet.addProperty("type", "ENDER_DRAGON");
        pet.addProperty("tier", "LEGENDARY");
        pet.addProperty("heldItem", "CROCHET_TIGER_PLUSHIE");

        assertEquals("Legendary Ender Dragon | Crochet Tiger Plushie", PlayerHud.activePetSummary(pet));
        assertNull(PlayerHud.activePetSummary(null));
    }

    @Test
    void rendersAvailableFieldsEvenWhenPlayerStatusIsUnavailable() {
        MarketGuardConfig.setPlayerHudPreset("profile");
        JsonObject player = new JsonObject();
        player.addProperty("status", "unavailable");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");
        player.addProperty("firstJoin", 1_715_478_978_620L);

        JsonObject profile = new JsonObject();
        profile.addProperty("name", "Apple");
        JsonObject wealth = new JsonObject();
        wealth.addProperty("bank", 42_000_000.0);
        profile.add("wealth", wealth);
        player.add("profile", profile);

        HudContent content = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false);
        List<String> lines = content.lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Pankraz01"));
        assertTrue(lines.contains("Player data unavailable"));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("First joined: ")));
        assertTrue(lines.contains("Profile: Apple"));
        assertTrue(lines.contains("Bank: 42,000,000"));
        assertTrue(lines.contains("UUID: fd9347ca546f4a4c89239665caa0385c"));
    }

    @Test
    void requestFailureStillShowsTheKnownTargetName() {
        MarketGuardConfig.setPlayerHudPreset("all");
        List<String> lines = PlayerHud.errorContent(new PlayerHud.Target("Pankraz01", null))
                .lines().stream().map(line -> line.getString()).toList();

        assertEquals(List.of("Pankraz01", "Player data unavailable"), lines);
    }

    @Test
    void requestFailureShowsSeenCountForAKnownUuid() {
        String uuid = "fd9347ca546f4a4c89239665caa0385c";
        try (MockedStatic<EncounterTracker> encounters = mockStatic(EncounterTracker.class)) {
            encounters.when(() -> EncounterTracker.timesSeen(uuid)).thenReturn(1);

            List<String> lines = PlayerHud.errorContent(new PlayerHud.Target(uuid, null))
                    .lines().stream().map(line -> line.getString()).toList();

            assertEquals(List.of(uuid, "Seen once", "Player data unavailable"), lines);
        }
    }

    @Test
    void requestFailureKeepsTheKnownUuidInsteadOfShowingNa() {
        MarketGuardConfig.setPlayerHudPreset("all");
        MarketGuardConfig.setPlayerHudShowUnavailableRows(true);
        String uuid = "fd9347ca546f4a4c89239665caa0385c";

        List<String> lines = PlayerHud.errorContent(new PlayerHud.Target(uuid, null))
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("UUID: " + uuid));
        assertFalse(lines.contains("UUID: n/a"));
    }

    @Test
    void requestFailureCachesTheBlacklistLookupWhileRendering() {
        String uuid = "fd9347ca546f4a4c89239665caa0385c";
        try (MockedStatic<ScamScreenerBlacklistCompat> blacklist = mockStatic(ScamScreenerBlacklistCompat.class)) {
            blacklist.when(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName(uuid)).thenReturn(null);

            PlayerHud.Target target = new PlayerHud.Target(uuid, null);
            PlayerHud.errorContent(target);
            PlayerHud.errorContent(target);

            blacklist.verify(() -> ScamScreenerBlacklistCompat.findBlacklistedPlayerName(uuid), times(1));
        }
    }

    @Test
    void requestFailureShowsUnavailableRowsWhenEnabled() {
        MarketGuardConfig.setPlayerHudPreset("all");
        MarketGuardConfig.setPlayerHudShowUnavailableRows(true);

        List<String> lines = PlayerHud.errorContent(new PlayerHud.Target("Hype_the_Time", null))
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.stream().noneMatch(line -> line.equals("All")));
        assertTrue(lines.contains("Bank + purse: n/a"));
        assertTrue(lines.contains("Active pet: n/a"));
        assertTrue(lines.contains("UUID: n/a"));
    }

    @Test
    void allPresetShowsEveryAvailablePlayerGroup() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");
        player.addProperty("firstJoin", 1_715_478_978_620L);

        JsonObject profile = new JsonObject();
        profile.addProperty("name", "Apple");
        JsonObject wealth = new JsonObject();
        wealth.addProperty("bank", 42_000_000.0);
        wealth.addProperty("purse", 1_000_000.0);
        profile.add("wealth", wealth);
        JsonObject pet = new JsonObject();
        pet.addProperty("type", "ENDER_DRAGON");
        profile.add("activePet", pet);
        JsonObject skills = new JsonObject();
        JsonObject mining = new JsonObject();
        mining.addProperty("level", 50);
        skills.add("mining", mining);
        profile.add("skills", skills);
        player.add("profile", profile);

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.stream().noneMatch(line -> line.equals("All")));
        assertTrue(lines.contains("Profile: Apple"));
        assertTrue(lines.contains("Bank + purse: 43,000,000"));
        assertTrue(lines.contains("Active pet: Ender Dragon"));
        assertTrue(lines.contains("Skills: Mining 50 | Avg 50.0 (1 skill)"));
        assertTrue(lines.contains("UUID: fd9347ca546f4a4c89239665caa0385c"));
        assertTrue(lines.contains("marketguard.hud.scamscreener.not_installed"));
    }

    @Test
    void estimatesOnlyVisibleBalancesAndPricedArmorOrEquipment() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        player.addProperty("name", "Pankraz01");

        JsonObject profile = new JsonObject();
        profile.addProperty("id", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        profile.addProperty("name", "Apple");
        JsonObject wealth = new JsonObject();
        wealth.addProperty("bank", 10_000_000.0);
        wealth.addProperty("purse", 1_000_000.0);
        JsonArray armor = new JsonArray();
        armor.add(item("HELMET", "Helmet", 1));
        wealth.add("armor", armor);
        JsonArray equipment = new JsonArray();
        equipment.add(item("BELT", "Belt", 2));
        wealth.add("equipment", equipment);
        profile.add("wealth", wealth);
        player.add("profile", profile);
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("ok", 11_000_000.0, null, List.of()),
                false,
                false,
                false
        ));

        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class);
             MockedStatic<LowestBinData> auctions = mockStatic(LowestBinData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("HELMET")).thenReturn(new BazaarData.LookupResult(
                    new BazaarData.Product("Helmet", 2_100_000.0, 2_000_000.0),
                    false,
                    false,
                    false
            ));
            bazaar.when(() -> BazaarData.lookupProduct("BELT"))
                    .thenReturn(new BazaarData.LookupResult(null, false, false, false));
            auctions.when(() -> LowestBinData.lookupPriceData("BELT"))
                    .thenReturn(new LowestBinData.LookupResult(
                            1_000_000.0,
                            1_050_000.0,
                            1_000_000.0,
                            false,
                            false,
                            false
                    ));

            List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();

            assertTrue(lines.contains("Profile: Apple"));
            assertTrue(lines.contains("Est. net worth: ~15,000,000"));
            assertTrue(lines.contains("Priced gear: 3/3 items"));
            assertTrue(lines.contains("Not included: inventory, pets"));
            assertTrue(lines.stream().noneMatch(line -> line.contains("Finance data") || line.contains("outdated")));
        }
    }

    @Test
    void reportsUnpricedGearInsteadOfPriceJargon() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        JsonArray armor = new JsonArray();
        armor.add(item("HELMET", "Helmet", 1));
        player.getAsJsonObject("profile").getAsJsonObject("wealth").add("armor", armor);
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("ok", 11_000_000.0, null, List.of()),
                false,
                false,
                false
        ));

        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class);
             MockedStatic<LowestBinData> auctions = mockStatic(LowestBinData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("HELMET"))
                    .thenReturn(new BazaarData.LookupResult(null, false, false, false));
            auctions.when(() -> LowestBinData.lookupPriceData("HELMET"))
                    .thenReturn(new LowestBinData.LookupResult(null, null, null, false, false, false));

            List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();

            assertTrue(lines.contains("Armor: Helmet"));
            assertTrue(lines.contains("Priced gear: 0/1 items"));
            assertTrue(lines.contains("Not included: inventory, pets, some gear prices"));
        }
    }

    @Test
    void showsServerMuseumValueAndExhibitsWithoutFinanceDiagnostics() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        PlayerFinanceData.Museum museum = new PlayerFinanceData.Museum(25_000_000.0, true, 2, 1);
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("partial", 130_000_000.0, museum, List.of("profile.finance.purse")),
                false,
                false,
                false
        ));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Est. net worth: ~130,000,000"));
        assertTrue(lines.contains("Museum: 25,000,000, appraised"));
        assertTrue(lines.contains("Museum: 2 exhibits (1 special)"));
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("Finance") || line.contains("ROI")));
    }

    @Test
    void privateFinanceDoesNotLeakReturnedValues() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse(
                        "private",
                        130_000_000.0,
                        new PlayerFinanceData.Museum(25_000_000.0, null, 0, 0),
                        List.of("profile.finance", "profile.museum")
                ),
                false,
                false,
                false
        ));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Finance data unavailable"));
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("Est. net worth") || line.startsWith("Museum")));
    }

    @Test
    void compactPresetHidesTheNetWorthLineWhileFinanceIsUnavailable() {
        MarketGuardConfig.setPlayerHudPreset("compact");
        JsonObject player = playerWithProfileIds();
        JsonObject wealth = player.getAsJsonObject("profile").getAsJsonObject("wealth");
        wealth.addProperty("purse", 1_000_000.0);
        JsonArray armor = new JsonArray();
        armor.add(item("HELMET", "Helmet", 1));
        wealth.add("armor", armor);
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(null, false, false, true));

        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("HELMET")).thenReturn(new BazaarData.LookupResult(
                    new BazaarData.Product("Helmet", 2_100_000.0, 2_000_000.0),
                    false,
                    false,
                    false
            ));

            List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();

            assertTrue(lines.contains("Purse: 1,000,000"));
            assertTrue(lines.stream().noneMatch(line -> line.startsWith("Est. net worth") || line.contains("Finance data")));
        }
    }

    @Test
    void emptyGearArraysShowNoPricedGearLine() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        JsonObject wealth = player.getAsJsonObject("profile").getAsJsonObject("wealth");
        wealth.add("armor", new JsonArray());
        wealth.add("equipment", new JsonArray());
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("ok", 11_000_000.0, null, List.of()),
                false,
                false,
                false
        ));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Est. net worth: ~11,000,000"));
        assertTrue(lines.stream().noneMatch(line -> line.startsWith("Priced gear")));
    }

    @Test
    void gearPricesStillLoadingShowTheLoadingWarning() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = playerWithProfileIds();
        JsonArray armor = new JsonArray();
        armor.add(item("HELMET", "Helmet", 1));
        player.getAsJsonObject("profile").getAsJsonObject("wealth").add("armor", armor);
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("ok", 11_000_000.0, null, List.of()),
                false,
                false,
                false
        ));

        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class);
             MockedStatic<LowestBinData> auctions = mockStatic(LowestBinData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("HELMET"))
                    .thenReturn(new BazaarData.LookupResult(null, false, true, false));
            auctions.when(() -> LowestBinData.lookupPriceData("HELMET"))
                    .thenReturn(new LowestBinData.LookupResult(null, null, null, false, true, false));

            List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();

            assertTrue(lines.contains("Priced gear: 0/1 items"));
            assertTrue(lines.contains("Loading finance data..."));
        }
    }

    @Test
    void leavesLowestBinOnlyPriceOutAsLowQuality() {
        try (MockedStatic<BazaarData> bazaar = mockStatic(BazaarData.class);
             MockedStatic<LowestBinData> auctions = mockStatic(LowestBinData.class)) {
            bazaar.when(() -> BazaarData.lookupProduct("HELMET"))
                    .thenReturn(new BazaarData.LookupResult(null, false, false, false));
            auctions.when(() -> LowestBinData.lookupPriceData("HELMET"))
                    .thenReturn(new LowestBinData.LookupResult(1_000_000.0, null, null, false, false, false));

            var price = PlayerHud.visibleItemPrice("HELMET");

            assertFalse(price.usable());
            assertTrue(price.lowQuality());
        }
    }

    @Test
    void unavailableRowsCanBeShownAsNaInsteadOfBeingHidden() {
        MarketGuardConfig.setPlayerHudPreset("all");
        MarketGuardConfig.setPlayerHudShowUnavailableRows(true);
        JsonObject player = new JsonObject();
        player.addProperty("status", "partial");
        player.addProperty("name", "Pankraz01");

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Profile: n/a"));
        assertTrue(lines.contains("Bank + purse: n/a"));
        assertTrue(lines.contains("Est. net worth: n/a"));
        assertTrue(lines.contains("Armor: n/a"));
        assertTrue(lines.contains("Equipment: n/a"));
        assertTrue(lines.contains("Active pet: n/a"));
        assertTrue(lines.contains("Skills: n/a"));
        assertTrue(lines.contains("UUID: n/a"));
    }

    @Test
    void unavailableRowsStayHiddenByDefault() {
        MarketGuardConfig.setPlayerHudPreset("all");
        JsonObject player = new JsonObject();
        player.addProperty("status", "partial");
        player.addProperty("name", "Pankraz01");

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.stream().noneMatch(line -> line.contains("n/a")));
    }

    private static JsonObject item(String id, String name, int count) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("name", name);
        item.addProperty("count", count);
        return item;
    }

    private static JsonObject playerWithProfileIds() {
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        player.addProperty("name", "Pankraz01");
        JsonObject profile = new JsonObject();
        profile.addProperty("id", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        profile.addProperty("name", "Apple");
        profile.add("wealth", new JsonObject());
        player.add("profile", profile);
        return player;
    }

    private static PlayerFinanceData.Response financeResponse(
            String status,
            Double knownTotal,
            PlayerFinanceData.Museum museum,
            List<String> unavailableFields
    ) {
        return new PlayerFinanceData.Response(
                status,
                false,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new PlayerFinanceData.Profile(
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        new PlayerFinanceData.Finance(10_000_000.0, 1_000_000.0, null, knownTotal),
                        museum
                ),
                unavailableFields
        );
    }
}
