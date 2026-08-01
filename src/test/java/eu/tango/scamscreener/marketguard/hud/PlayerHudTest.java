package eu.tango.scamscreener.marketguard.hud;

import com.google.gson.JsonObject;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.tangosHudLib.api.HudContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                "title", "name", "status", "seen", "first_join", "profile", "wealth",
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

        assertTrue(lines.contains("All"));
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

        assertTrue(lines.contains("All"));
        assertTrue(lines.contains("Profile: Apple"));
        assertTrue(lines.contains("Bank: 42,000,000 | Purse: 1,000,000"));
        assertTrue(lines.contains("Active pet: Ender Dragon"));
        assertTrue(lines.contains("Skills: Mining 50"));
        assertTrue(lines.contains("UUID: fd9347ca546f4a4c89239665caa0385c"));
        assertTrue(lines.contains("marketguard.hud.scamscreener.no_entry"));
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
}
