package eu.tango.scamscreener.marketguard.hud;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.screen.HudScreenGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudCustomizationTest {
    @AfterEach
    void reset() {
        MarketGuardConfig.setPlayerHudPreset("trade");
        PlayerHud.setPreset("trade");
        MarketGuardConfig.auctionPriceHudScreens = new ArrayList<>(List.of("bin_view"));
        MarketGuardConfig.auctionPriceHudRows = new ArrayList<>(List.of("item", "auction", "lowest_bin", "difference", "advice", "volatility", "liquidity", "stale"));
        MarketGuardConfig.playerHudScreens = new ArrayList<>(List.of("trade", "profile", "bin_view"));
        MarketGuardConfig.playerHudRows = new ArrayList<>(List.of(
                "name", "status", "seen", "first_join", "profile", "wealth",
                "profile_value", "value_coverage", "value_missing", "value_status",
                "museum", "museum_items", "finance_status", "finance_history",
                "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable"
        ));
        MarketGuardConfig.tradeGuardHudScreens = new ArrayList<>(List.of("trade"));
        MarketGuardConfig.tradeGuardHudRows = new ArrayList<>(List.of("own_value", "partner_value", "difference", "unpriced", "data", "warning"));
    }

    @Test
    void defaultMappingsOnlyShowHudOnTheirIntendedScreens() {
        assertTrue(HudCustomization.visible(HudCustomization.HudId.AUCTION_PRICE, "Bin Auction View"));
        assertFalse(HudCustomization.visible(HudCustomization.HudId.AUCTION_PRICE, "Auction House"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "Pankraz01's Profile"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "You Pankraz01"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.PLAYER, "Bin Auction View"));
        assertTrue(HudCustomization.visible(HudCustomization.HudId.TRADE_GUARD, "You Pankraz01"));
        assertFalse(HudCustomization.visible(HudCustomization.HudId.TRADE_GUARD, "Pankraz01's Profile"));
    }

    @Test
    void rowVisibilityAndOrderAreConfigurable() {
        HudCustomization.toggleRow(HudCustomization.HudId.AUCTION_PRICE, "difference");
        HudCustomization.moveRow(HudCustomization.HudId.AUCTION_PRICE, "advice", -3);

        assertFalse(HudCustomization.rowEnabled(HudCustomization.HudId.AUCTION_PRICE, "difference"));
        assertEquals(List.of("advice", "item", "auction", "lowest_bin", "volatility", "liquidity", "stale"),
                HudCustomization.rows(HudCustomization.HudId.AUCTION_PRICE));
    }

    @Test
    void dragAndDropMovesRowsToTheRequestedIndexAndKeepsVisibility() {
        HudCustomization.toggleRow(HudCustomization.HudId.AUCTION_PRICE, "difference");
        HudCustomization.moveRowTo(HudCustomization.HudId.AUCTION_PRICE, "difference", 1);

        assertEquals(List.of("item", "!difference", "auction", "lowest_bin", "advice", "volatility", "liquidity", "stale"),
                MarketGuardConfig.auctionPriceHudRows);
        assertFalse(HudCustomization.rowEnabled(HudCustomization.HudId.AUCTION_PRICE, "difference"));
    }

    @Test
    void rowsCanBeDroppedBetweenVisibleAndHiddenColumns() {
        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "difference", false, 0);
        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "item", false, 0);

        assertEquals(List.of("auction", "lowest_bin", "advice", "volatility", "liquidity", "stale", "!item", "!difference"),
                MarketGuardConfig.auctionPriceHudRows);
        assertEquals(List.of("auction", "lowest_bin", "advice", "volatility", "liquidity", "stale"),
                HudCustomization.editableRows(HudCustomization.HudId.AUCTION_PRICE, true));
        assertEquals(List.of("item", "difference"),
                HudCustomization.editableRows(HudCustomization.HudId.AUCTION_PRICE, false));

        HudCustomization.placeRow(HudCustomization.HudId.AUCTION_PRICE, "difference", true, 1);

        assertEquals(List.of("auction", "difference", "lowest_bin", "advice", "volatility", "liquidity", "stale", "!item"),
                MarketGuardConfig.auctionPriceHudRows);
    }

    @Test
    void editorRowsUseHudExamplesInsteadOfConfigurationLabels() {
        assertEquals("This auction: 15.0m",
                HudCustomization.example(HudCustomization.HudId.AUCTION_PRICE, "auction").getString());
        assertEquals("Bank: 42.0m | Purse: 3.2m",
                HudCustomization.example(HudCustomization.HudId.PLAYER, "wealth").getString());
        assertEquals("Total: +12.5m",
                HudCustomization.example(HudCustomization.HudId.PROFIT_TRACKER, "total").getString());
    }

    @Test
    void playerPresetChangesTheAvailableRowsWithoutDiscardingConfiguration() {
        MarketGuardConfig.setPlayerHudPreset("compact");
        PlayerHud.setPreset("compact");
        assertEquals(List.of(
                        "name", "status", "seen", "wealth", "profile_value", "value_coverage", "value_status", "finance_status",
                        "scamscreener", "data", "unavailable"
                ),
                HudCustomization.rows(HudCustomization.HudId.PLAYER));

        MarketGuardConfig.setPlayerHudPreset("all");
        PlayerHud.setPreset("all");
        assertTrue(HudCustomization.rows(HudCustomization.HudId.PLAYER).contains("armor"));
    }

    @Test
    void editorTableStaysAboveFooterAtTheTestedGuiScale() {
        int rowsTop = HudEditorScreen.calculateRowsTop(428);
        int listHeight = HudEditorScreen.calculateListHeight(240, rowsTop);

        assertEquals(4, HudEditorScreen.calculateScreenColumns(428));
        assertEquals(128, rowsTop);
        assertEquals(72, listHeight);
        assertEquals(12, (240 - 28) - (rowsTop + listHeight));
    }

    @Test
    void screenTogglesChangeTheConfiguredMapping() {
        HudCustomization.toggleScreen(HudCustomization.HudId.AUCTION_PRICE, HudScreenGroup.AUCTION_HOUSE);

        assertTrue(HudCustomization.visible(HudCustomization.HudId.AUCTION_PRICE, "Auction House"));
    }
}
