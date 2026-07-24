package eu.tango.scamscreener.marketguard.profittracker;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.hud.ProfitTrackerHud;
import eu.tango.tangosHudLib.api.HudContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitTrackerHudTest {
    @AfterEach
    void resetDisplay() {
        MarketGuardConfig.setProfitTrackerHudEnabled(false);
        ProfitTracker.resetForTests();
    }

    @Test
    void hidesWidgetWhenDisplayIsDisabled() {
        MarketGuardConfig.setProfitTrackerHudEnabled(false);

        assertFalse(ProfitTrackerHud.content(true, null).visible());
    }

    @Test
    void hidesWidgetBeforeSkyBlockSession() {
        MarketGuardConfig.setProfitTrackerHudEnabled(true);

        assertFalse(ProfitTrackerHud.content(true, null).visible());
    }

    @Test
    void hidesWidgetWithoutAPlayer() {
        MarketGuardConfig.setProfitTrackerHudEnabled(true);
        assertTrue(ProfitTracker.tryStartSkyBlockSession("Welcome to Hypixel SkyBlock!"));

        assertFalse(ProfitTrackerHud.content(false, "orange").visible());
    }

    @Test
    void showsProfileHintWhenDisplayIsEnabledWithoutProfile() {
        MarketGuardConfig.setProfitTrackerHudEnabled(true);
        assertTrue(ProfitTracker.tryStartSkyBlockSession("Welcome to Hypixel SkyBlock!"));

        HudContent content = ProfitTrackerHud.content(true, null);

        assertTrue(content.visible());
        assertEquals("Profit Tracker", content.lines().getFirst().getString());
        assertEquals("No SkyBlock profile detected.", content.lines().get(1).getString());
    }

    @Test
    void showsAllProfitSourcesAndTotalForProfile() {
        ProfitTrackerState state = new ProfitTrackerState();
        ProfileProfitState profile = state.getOrCreateProfile("orange");
        profile.bazaarAllTimeProfit = 1_500.0;
        profile.auctionHouseAllTimeProfit = -250.0;
        profile.minionAllTimeProfit = 500.0;
        profile.interestAllTimeProfit = 125.0;
        profile.allowanceAllTimeProfit = 100.0;
        ProfitTracker.setStateForTests(state);
        MarketGuardConfig.setProfitTrackerHudEnabled(true);
        assertTrue(ProfitTracker.tryStartSkyBlockSession("Welcome to Hypixel SkyBlock!"));

        HudContent content = ProfitTrackerHud.content(true, "orange");

        assertProfitLine(content.lines().get(1), "Bazaar: +1,500", ChatFormatting.GREEN);
        assertProfitLine(content.lines().get(2), "Auction House: -250", ChatFormatting.RED);
        assertProfitLine(content.lines().get(3), "Minion: +500", ChatFormatting.GREEN);
        assertProfitLine(content.lines().get(4), "Interest: +125", ChatFormatting.GREEN);
        assertProfitLine(content.lines().get(5), "Allowance: +100", ChatFormatting.GREEN);
        assertProfitLine(content.lines().get(6), "Total: +1,975", ChatFormatting.GREEN);
    }

    private static void assertProfitLine(Component line, String expectedText, ChatFormatting signColor) {
        assertEquals(expectedText, line.getString());
        assertEquals(color(ChatFormatting.WHITE), line.getStyle().getColor().getValue());
        assertEquals(color(signColor), line.getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(color(ChatFormatting.GOLD), line.getSiblings().get(1).getStyle().getColor().getValue());
    }

    private static int color(ChatFormatting formatting) {
        return TextColor.fromLegacyFormat(formatting).getValue();
    }
}
