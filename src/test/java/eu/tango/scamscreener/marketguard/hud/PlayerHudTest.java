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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class PlayerHudTest {
    @AfterEach
    void reset() {
        PlayerHud.resetForTests();
        MarketGuardConfig.setPlayerHudPreset("trade");
        MarketGuardConfig.playerHudRows = new ArrayList<>(List.of(
                "name", "status", "seen", "first_join", "profile", "wealth",
                "profile_value", "value_coverage", "value_missing", "value_status",
                "museum", "museum_items", "finance_status", "finance_history",
                "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable"
        ));
    }

    @Test
    void showsNeutralStatusForEveryNonSuccessfulPlayerResult() {
        assertNull(PlayerHud.statusMessage("ok"));
        assertEquals("Player data is partial", PlayerHud.statusMessage("partial"));
        assertEquals("Player not found", PlayerHud.statusMessage("not_found"));
        assertEquals("SkyBlock profile not found", PlayerHud.statusMessage("profile_not_found"));
        assertEquals("SkyBlock profile unavailable", PlayerHud.statusMessage("profile_unavailable"));
        assertEquals("Player data unavailable", PlayerHud.statusMessage("unavailable"));
    }

    @Test
    void compactPresetStartsWithThePlayerAndFormatsSeenLikeTheEditorPreview() {
        MarketGuardConfig.setPlayerHudPreset("compact");
        PlayerHud.setPreset("compact");
        JsonObject player = new JsonObject();
        player.addProperty("status", "ok");
        player.addProperty("uuid", "fd9347ca546f4a4c89239665caa0385c");
        player.addProperty("name", "Pankraz01");

        try (MockedStatic<EncounterTracker> encounters = mockStatic(EncounterTracker.class)) {
            encounters.when(() -> EncounterTracker.timesSeen("fd9347ca546f4a4c89239665caa0385c")).thenReturn(12);

            List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                    .lines().stream().map(line -> line.getString()).toList();

            assertEquals("Pankraz01", lines.getFirst());
            assertTrue(lines.contains("Seen: 12 times"));
            assertFalse(lines.contains("Compact"));
        }
    }

    @Test
    void showsOnlyApiDataQualityActuallyProvidedByTheResponse() {
        JsonObject hypixel = new JsonObject();
        hypixel.addProperty("source", "hypixel");
        hypixel.addProperty("fetchedAt", 1_715_478_978_620L);
        assertEquals("Hypixel API", PlayerHud.dataQuality(hypixel).split(" • ")[0]);

        JsonObject mojang = new JsonObject();
        mojang.addProperty("source", "mojang");
        assertEquals("Mojang API", PlayerHud.dataQuality(mojang));

        assertNull(PlayerHud.dataQuality(new JsonObject()));
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
        PlayerHud.setPreset("profile");
        PlayerHud.setPreset("profile");
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
        assertTrue(lines.contains("Bank: 42,000,000 | Purse: ?"));
        assertTrue(lines.contains("UUID: fd9347ca546f4a4c89239665caa0385c"));
    }

    @Test
    void requestFailureStillShowsTheKnownTargetName() {
        MarketGuardConfig.setPlayerHudPreset("all");
        List<String> lines = PlayerHud.errorContent(new PlayerHud.Target("Pankraz01", null))
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Pankraz01"));
        assertTrue(lines.contains("Player data unavailable"));
        assertTrue(lines.stream().anyMatch(line -> line.equals("marketguard.hud.scamscreener.installed")
                || line.equals("marketguard.hud.scamscreener.not_installed")));
        assertTrue(lines.contains("Unavailable: Player API response"));
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
        PlayerHud.setPreset("all");
        MarketGuardConfig.setPlayerHudShowUnavailableRows(true);

        List<String> lines = PlayerHud.errorContent(new PlayerHud.Target("Hype_the_Time", null))
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.stream().noneMatch(line -> line.equals("All")));
        assertTrue(lines.contains("Bank: n/a | Purse: n/a"));
        assertTrue(lines.contains("Active pet: n/a"));
        assertTrue(lines.contains("UUID: n/a"));
    }

    @Test
    void allPresetShowsEveryAvailablePlayerGroup() {
        MarketGuardConfig.setPlayerHudPreset("all");
        PlayerHud.setPreset("all");
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
        assertTrue(lines.contains("Bank: 42,000,000 | Purse: 1,000,000"));
        assertTrue(lines.contains("Active pet: Ender Dragon"));
        assertTrue(lines.contains("Skills: Mining 50 | Visible avg 50.0 (1 skill)"));
        assertTrue(lines.contains("UUID: fd9347ca546f4a4c89239665caa0385c"));
        assertTrue(lines.contains("marketguard.hud.scamscreener.no_entry"));
    }

    @Test
    void estimatesOnlyVisibleBalancesAndPricedArmorOrEquipment() {
        MarketGuardConfig.setPlayerHudPreset("all");
        PlayerHud.setPreset("all");
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

            assertTrue(lines.contains("Profile: Apple | ID: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
            assertTrue(lines.contains("Known profile value estimate: 15,000,000 coins"));
            assertTrue(lines.contains("Priced visible gear: 3/3 items"));
            assertTrue(lines.contains("Not included: inventory, pets"));
            assertTrue(lines.contains("Estimate data: finance API + cached market prices"));
        }
    }

    @Test
    void showsServerMuseumValueOwnershipAndPartialStateWithoutInventingRoi() {
        MarketGuardConfig.setPlayerHudPreset("all");
        PlayerHud.setPreset("all");
        JsonObject player = playerWithProfileIds();
        PlayerFinanceData.Museum museum = new PlayerFinanceData.Museum(
                25_000_000.0,
                true,
                List.of("HYPERION", "TERMINATOR"),
                2,
                List.of("DCTR_SPACE_HELM"),
                1
        );
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse("partial", 130_000_000.0, museum, List.of("profile.finance.purse")),
                false,
                false,
                false
        ));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Known profile value estimate: 130,000,000 coins"));
        assertTrue(lines.contains("Museum: Value: 25,000,000 coins | Appraisal: available"));
        assertTrue(lines.contains("Museum ownership: 2 donated exhibits | 1 special exhibits"));
        assertTrue(lines.contains("Finance & museum: partial"));
        assertTrue(lines.contains("Income, costs & ROI: unavailable"));
    }

    @Test
    void privateFinanceDoesNotLeakReturnedValues() {
        MarketGuardConfig.setPlayerHudPreset("all");
        PlayerHud.setPreset("all");
        JsonObject player = playerWithProfileIds();
        PlayerHud.setFinanceLookupForTests((uuid, profileId) -> new PlayerFinanceData.LookupResult(
                financeResponse(
                        "private",
                        130_000_000.0,
                        new PlayerFinanceData.Museum(25_000_000.0, null, List.of(), 0, List.of(), 0),
                        List.of("profile.finance", "profile.museum")
                ),
                false,
                false,
                false
        ));

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Known profile value estimate: unavailable"));
        assertTrue(lines.contains("Finance data: private"));
        assertTrue(lines.contains("Finance & museum: private"));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Museum: Value:")));
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
        PlayerHud.setPreset("all");
        MarketGuardConfig.setPlayerHudShowUnavailableRows(true);
        JsonObject player = new JsonObject();
        player.addProperty("status", "partial");
        player.addProperty("name", "Pankraz01");

        List<String> lines = PlayerHud.playerContent(new PlayerHud.Target("Pankraz01", null), player, false, false)
                .lines().stream().map(line -> line.getString()).toList();

        assertTrue(lines.contains("Profile: n/a"));
        assertTrue(lines.contains("Bank: n/a | Purse: n/a"));
        assertTrue(lines.contains("Armor: n/a"));
        assertTrue(lines.contains("Equipment: n/a"));
        assertTrue(lines.contains("Active pet: n/a"));
        assertTrue(lines.contains("Skills: n/a"));
        assertTrue(lines.contains("UUID: n/a"));
    }

    @Test
    void unavailableRowsStayHiddenByDefault() {
        PlayerHud.setPreset("all");
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
                1715478978620L,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                new PlayerFinanceData.Profile(
                        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "Apple",
                        true,
                        new PlayerFinanceData.Finance(10_000_000.0, 1_000_000.0, null, knownTotal),
                        museum
                ),
                unavailableFields
        );
    }
}
