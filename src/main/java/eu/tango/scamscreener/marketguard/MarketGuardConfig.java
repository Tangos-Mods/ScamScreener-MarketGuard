package eu.tango.scamscreener.marketguard;

import eu.midnightdust.lib.config.MidnightConfig;
import eu.midnightdust.lib.config.MidnightConfigListWidget;
import eu.midnightdust.lib.config.MidnightConfigScreen;
import eu.midnightdust.lib.config.EntryInfo;
import eu.tango.scamscreener.marketguard.hud.HudCustomization;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;
import eu.tango.scamscreener.marketguard.profittracker.ProfitTrackerResetScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public final class MarketGuardConfig extends MidnightConfig {
    public static final long DEFAULT_ABSOLUTE_THRESHOLD = 10_000L;
    private static final String PROTECTION = "protection";
    private static final String HUD = "hud";
    private static final String TRACKER = "tracker";
    private static final String GENERAL = "general";

    @Entry(category = PROTECTION, min = 0, max = 100, isSlider = true)
    public static int underbiddingThreshold = AuctionUnderbidding.DEFAULT_THRESHOLD;
    @Entry(category = PROTECTION, min = 100, max = 200, isSlider = true)
    public static int overbiddingThreshold = AuctionOverbidding.DEFAULT_THRESHOLD;
    @Entry(category = PROTECTION, min = 0, max = Long.MAX_VALUE)
    public static double absoluteThreshold = DEFAULT_ABSOLUTE_THRESHOLD;
    @Entry(category = GENERAL)
    public static boolean debug = false;
    @Entry(category = TRACKER)
    public static boolean warnOnUnmatchedProfitConfirmations = false;
    @Entry(category = HUD)
    public static boolean profitTrackerHudEnabled = false;
    @Entry(category = HUD)
    @Hidden
    public static PlayerHudPreset playerHudPreset = PlayerHudPreset.trade;
    @Entry(category = HUD)
    public static boolean playerHudShowUnavailableRows = false;
    @Entry(category = HUD)
    public static boolean shortNumberFormat = false;

    @Entry(category = HUD)
    @Hidden
    public static List<String> auctionPriceHudScreens = new ArrayList<>(List.of("bin_view"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> playerHudScreens = new ArrayList<>(List.of("trade", "profile", "bin_view"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> tradeGuardHudScreens = new ArrayList<>(List.of("trade"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> minionProfitHudScreens = new ArrayList<>(List.of("minion"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> forgeProfitHudScreens = new ArrayList<>(List.of("forge"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> profitTrackerHudScreens = new ArrayList<>(List.of("ingame"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> auctionPriceHudRows = new ArrayList<>(List.of("item", "auction", "lowest_bin", "difference", "advice", "volatility", "liquidity", "stale"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> playerHudRows = new ArrayList<>(List.of(
            "name", "status", "seen", "first_join", "profile", "wealth",
            "profile_value", "value_coverage", "value_missing", "value_status",
            "museum", "museum_items", "finance_status", "finance_history",
            "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable"
    ));
    @Entry(category = HUD)
    @Hidden
    public static List<String> tradeGuardHudRows = new ArrayList<>(List.of("own_value", "partner_value", "difference", "unpriced", "data", "warning"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> minionProfitHudRows = new ArrayList<>(List.of("held_coins", "profit", "forecast", "forecast_status", "missing", "loading", "unavailable", "stale"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> forgeProfitHudRows = new ArrayList<>(List.of("profit", "missing", "loading", "unavailable", "stale"));
    @Entry(category = HUD)
    @Hidden
    public static List<String> profitTrackerHudRows = new ArrayList<>(List.of("bazaar", "auction_house", "minion", "interest", "allowance", "total"));

    public MarketGuardConfig() {}

    public static void load() {
        MidnightConfig.init(MarketGuard.MOD_ID, MarketGuardConfig.class);
        if (normalizeValues()) {
            save();
        }
    }

    public static boolean save() {
        if (!MidnightConfig.configInstances.containsKey(MarketGuard.MOD_ID)) {
            return false;
        }

        try {
            MidnightConfig.write(MarketGuard.MOD_ID);
            return true;
        } catch (RuntimeException exception) {
            MarketGuard.LOGGER.warn("Failed to save MarketGuard config", exception);
            return false;
        }
    }

    public static int getUnderbiddingThreshold() {
        return underbiddingThreshold;
    }

    public static void setUnderbiddingThreshold(int threshold) {
        if (threshold < 0 || threshold > 100) {
            throw new IllegalArgumentException("underbidding threshold must be between 0 and 100");
        }
        underbiddingThreshold = threshold;
    }

    public static int getOverbiddingThreshold() {
        return overbiddingThreshold;
    }

    public static void setOverbiddingThreshold(int threshold) {
        if (threshold < 100) {
            throw new IllegalArgumentException("overbidding threshold must be at least 100");
        }
        overbiddingThreshold = threshold;
    }

    public static boolean isDebugEnabled() {
        return debug;
    }

    public static void setDebugEnabled(boolean enabled) {
        debug = enabled;
    }

    public static long getAbsoluteThreshold() {
        return Math.max(0L, Math.round(absoluteThreshold));
    }

    public static void setAbsoluteThreshold(long threshold) {
        if (threshold < 0L) {
            throw new IllegalArgumentException("absolute threshold must not be negative");
        }
        absoluteThreshold = threshold;
    }

    public static boolean isWarnOnUnmatchedProfitConfirmations() {
        return warnOnUnmatchedProfitConfirmations;
    }

    public static void setWarnOnUnmatchedProfitConfirmations(boolean enabled) {
        warnOnUnmatchedProfitConfirmations = enabled;
    }

    public static boolean isProfitTrackerHudEnabled() {
        return profitTrackerHudEnabled;
    }

    public static void setProfitTrackerHudEnabled(boolean enabled) {
        profitTrackerHudEnabled = enabled;
    }

    public static String getPlayerHudPreset() {
        return playerHudPreset.name();
    }

    public static void setPlayerHudPreset(String preset) {
        String normalized = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
        try {
            playerHudPreset = PlayerHudPreset.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown player HUD preset", exception);
        }
    }

    public static boolean isShortNumberFormat() {
        return shortNumberFormat;
    }

    public static void setShortNumberFormat(boolean enabled) {
        shortNumberFormat = enabled;
    }

    public static boolean isPlayerHudShowUnavailableRows() {
        return playerHudShowUnavailableRows;
    }

    public static void setPlayerHudShowUnavailableRows(boolean show) {
        playerHudShowUnavailableRows = show;
    }

    @Override
    public Path getJsonFilePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("scamscreener_marketguard")
                .resolve("config.json");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void writeChanges(String modid) {
        try {
            Files.createDirectories(getJsonFilePath().getParent());
        } catch (IOException exception) {
            MarketGuard.LOGGER.warn("Failed to create MarketGuard config directory", exception);
            return;
        }

        super.writeChanges(modid);
        PlayerHud.setPreset(getPlayerHudPreset());
    }

    @Override
    public void onTabInit(String category, MidnightConfigListWidget list, MidnightConfigScreen screen) {
        super.onTabInit(category, list, screen);
        HudCustomization.addSettingsButtons(category, list, screen);
        if (HUD.equals(category)) {
            int controlsX = list.getWidth() - 185;
            list.addButton(List.of(Button.builder(
                            Component.translatable("marketguard.profit_tracker.reset").withStyle(ChatFormatting.RED),
                            button -> ProfitTrackerResetScreen.open(screen)
                    ).bounds(controlsX, 0, 150, 20).build()),
                    Component.translatable("marketguard.profit_tracker.data"), hudEntryInfo());
        }
    }

    public static EntryInfo hudEntryInfo() {
        return new EntryInfo(null, MarketGuard.MOD_ID);
    }

    private static boolean normalizeValues() {
        boolean changed = false;
        if (underbiddingThreshold < 0 || underbiddingThreshold > 100) {
            underbiddingThreshold = AuctionUnderbidding.DEFAULT_THRESHOLD;
            changed = true;
        }
        if (overbiddingThreshold < 100) {
            overbiddingThreshold = AuctionOverbidding.DEFAULT_THRESHOLD;
            changed = true;
        }
        if (!Double.isFinite(absoluteThreshold) || absoluteThreshold < 0.0D) {
            absoluteThreshold = DEFAULT_ABSOLUTE_THRESHOLD;
            changed = true;
        }
        if (playerHudPreset == null) {
            playerHudPreset = PlayerHudPreset.trade;
            changed = true;
        }
        if (auctionPriceHudScreens == null) { auctionPriceHudScreens = new ArrayList<>(); changed = true; }
        if (playerHudScreens == null) { playerHudScreens = new ArrayList<>(); changed = true; }
        if (tradeGuardHudScreens == null) { tradeGuardHudScreens = new ArrayList<>(); changed = true; }
        if (minionProfitHudScreens == null) { minionProfitHudScreens = new ArrayList<>(); changed = true; }
        if (forgeProfitHudScreens == null) { forgeProfitHudScreens = new ArrayList<>(); changed = true; }
        if (profitTrackerHudScreens == null) { profitTrackerHudScreens = new ArrayList<>(); changed = true; }
        if (auctionPriceHudRows == null) { auctionPriceHudRows = new ArrayList<>(); changed = true; }
        if (playerHudRows == null) { playerHudRows = new ArrayList<>(); changed = true; }
        if (tradeGuardHudRows == null) { tradeGuardHudRows = new ArrayList<>(); changed = true; }
        if (minionProfitHudRows == null) { minionProfitHudRows = new ArrayList<>(); changed = true; }
        if (forgeProfitHudRows == null) { forgeProfitHudRows = new ArrayList<>(); changed = true; }
        if (profitTrackerHudRows == null) { profitTrackerHudRows = new ArrayList<>(); changed = true; }
        changed |= normalizeList(auctionPriceHudScreens, List.of("bin_view"), List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge"));
        changed |= normalizeList(playerHudScreens, List.of("trade", "profile", "bin_view"), List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge"));
        changed |= normalizeList(tradeGuardHudScreens, List.of("trade"), List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge"));
        changed |= normalizeList(minionProfitHudScreens, List.of("minion"), List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge"));
        changed |= normalizeList(forgeProfitHudScreens, List.of("forge"), List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge"));
        changed |= normalizeList(profitTrackerHudScreens, List.of("ingame"), List.of("ingame", "auction_house", "bin_view", "trade", "profile", "minion", "forge"));
        changed |= normalizeRows(auctionPriceHudRows, List.of("item", "auction", "lowest_bin", "difference", "advice", "volatility", "liquidity", "stale"));
        changed |= normalizeRows(playerHudRows, List.of(
                "name", "status", "seen", "first_join", "profile", "wealth",
                "profile_value", "value_coverage", "value_missing", "value_status",
                "museum", "museum_items", "finance_status", "finance_history",
                "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable"
        ));
        changed |= normalizeRows(tradeGuardHudRows, List.of("own_value", "partner_value", "difference", "unpriced", "data", "warning"));
        changed |= normalizeRows(minionProfitHudRows, List.of("held_coins", "profit", "forecast", "forecast_status", "missing", "loading", "unavailable", "stale"));
        changed |= normalizeRows(forgeProfitHudRows, List.of("profit", "missing", "loading", "unavailable", "stale"));
        changed |= normalizeRows(profitTrackerHudRows, List.of("bazaar", "auction_house", "minion", "interest", "allowance", "total"));
        return changed;
    }

    private static boolean normalizeList(List<String> values, List<String> defaults, List<String> allowed) {
        List<String> normalized = values == null ? new ArrayList<>() : values.stream()
                .filter(value -> value != null && allowed.contains(value))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            normalized = new ArrayList<>(defaults);
        }
        if (values == null || !values.equals(normalized)) {
            if (values != null) {
                values.clear();
                values.addAll(normalized);
            }
            return true;
        }
        return false;
    }

    private static boolean normalizeRows(List<String> values, List<String> defaults) {
        if (values == null) {
            return false;
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            boolean disabled = value.startsWith("!");
            String id = disabled ? value.substring(1) : value;
            if (defaults.contains(id) && !normalized.contains(id) && !normalized.contains("!" + id)) {
                normalized.add(disabled ? "!" + id : id);
            }
        }
        for (String id : defaults) {
            if (!normalized.contains(id) && !normalized.contains("!" + id)) {
                normalized.add(id);
            }
        }
        if (!values.equals(normalized)) {
            values.clear();
            values.addAll(normalized);
            return true;
        }
        return false;
    }

    public enum PlayerHudPreset {
        trade,
        compact,
        profile,
        all
    }
}
