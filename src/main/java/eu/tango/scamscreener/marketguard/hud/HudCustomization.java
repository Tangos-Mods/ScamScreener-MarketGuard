package eu.tango.scamscreener.marketguard.hud;

import eu.midnightdust.lib.config.MidnightConfigListWidget;
import eu.midnightdust.lib.config.MidnightConfigScreen;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.screen.HudScreenGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HudCustomization {
    private static volatile String currentScreenTitle;
    public enum HudId {
        AUCTION_PRICE("auctionPrice"),
        PLAYER("player"),
        MINION_PROFIT("minionProfit"),
        FORGE_PROFIT("forgeProfit"),
        PROFIT_TRACKER("profitTracker");

        private final String key;

        HudId(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

    }

    private HudCustomization() {}

    public static void setCurrentScreenTitle(String title) {
        currentScreenTitle = title;
    }

    public static boolean visible(HudId hud, String title) {
        Set<HudScreenGroup> current = HudScreenGroup.classify(title);
        for (HudScreenGroup group : current) {
            if (screens(hud).contains(group.name().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean visibleOnCurrentScreen(HudId hud) {
        return visible(hud, currentScreenTitle);
    }

    public static List<String> rows(HudId hud) {
        List<String> configured = rowValues(hud);
        List<String> defaults = availableRows(hud);
        List<String> result = new ArrayList<>();
        for (String value : configured) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String id = value.startsWith("!") ? value.substring(1) : value;
            if (defaults.contains(id) && !value.startsWith("!")) {
                result.add(id);
            }
        }
        for (String id : defaults) {
            if (!configured.contains(id) && !configured.contains("!" + id)) {
                result.add(id);
            }
        }
        return result;
    }

    public static boolean rowEnabled(HudId hud, String row) {
        return rowValues(hud).stream().noneMatch(value -> value.equals("!" + row));
    }

    static boolean screenEnabled(HudId hud, HudScreenGroup group) {
        return screens(hud).contains(group.name().toLowerCase());
    }

    static List<String> editableRows(HudId hud, boolean enabled) {
        return rowValues(hud).stream()
                .map(value -> value.startsWith("!") ? value.substring(1) : value)
                .filter(availableRows(hud)::contains)
                .filter(row -> rowEnabled(hud, row) == enabled)
                .toList();
    }

    public static void toggleScreen(HudId hud, HudScreenGroup group) {
        List<String> values = screens(hud);
        String key = group.name().toLowerCase();
        if (!values.remove(key)) {
            values.add(key);
        }
        MarketGuardConfig.save();
    }

    public static void toggleRow(HudId hud, String row) {
        List<String> values = rowValues(hud);
        if (values.remove(row)) {
            values.add("!" + row);
        } else if (values.remove("!" + row)) {
            values.add(row);
        } else {
            values.add("!" + row);
        }
        MarketGuardConfig.save();
    }

    public static void moveRow(HudId hud, String row, int direction) {
        List<String> values = rowValues(hud);
        int index = values.indexOf(row);
        if (index < 0) {
            index = values.indexOf("!" + row);
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= values.size()) {
            return;
        }
        String value = values.remove(index);
        values.add(target, value);
        MarketGuardConfig.save();
    }

    static void moveRowTo(HudId hud, String row, int target) {
        List<String> values = rowValues(hud);
        int index = values.indexOf(row);
        if (index < 0) {
            index = values.indexOf("!" + row);
        }
        if (index < 0 || target < 0 || target >= values.size() || index == target) {
            return;
        }
        String value = values.remove(index);
        values.add(target, value);
        MarketGuardConfig.save();
    }

    static void placeRow(HudId hud, String row, boolean enabled, int target) {
        if (!defaultRows(hud).contains(row)) {
            return;
        }

        List<String> visibleRows = new ArrayList<>(editableRows(hud, true));
        List<String> hiddenRows = new ArrayList<>(editableRows(hud, false));
        visibleRows.remove(row);
        hiddenRows.remove(row);

        List<String> destination = enabled ? visibleRows : hiddenRows;
        destination.add(Math.max(0, Math.min(target, destination.size())), row);

        List<String> values = rowValues(hud);
        values.clear();
        values.addAll(visibleRows);
        hiddenRows.forEach(hidden -> values.add("!" + hidden));
        MarketGuardConfig.save();
    }

    static Component example(HudId hud, String row) {
        return switch (hud) {
            case AUCTION_PRICE -> switch (row) {
                case "title" -> Component.literal("Auction Price").withStyle(ChatFormatting.AQUA);
                case "item" -> Component.literal("6th Anniversary Barn Skin").withStyle(ChatFormatting.WHITE);
                case "auction" -> Component.literal("This auction: 15.0m").withStyle(ChatFormatting.GOLD);
                case "lowest_bin" -> Component.literal("Lowest BIN: 15.4m").withStyle(ChatFormatting.YELLOW);
                case "difference" -> Component.literal("Difference to 7d avg: -2.6m (-14.7%)").withStyle(ChatFormatting.GREEN);
                case "advice" -> Component.literal("Price is near Lowest BIN.").withStyle(ChatFormatting.GRAY);
                case "stale" -> Component.literal("Lowest BIN data may be outdated.").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case PLAYER -> switch (row) {
                case "title" -> Component.literal("Player Profile").withStyle(ChatFormatting.AQUA);
                case "name" -> Component.literal("Name: TangoDev").withStyle(ChatFormatting.WHITE);
                case "status" -> Component.literal("Status: available").withStyle(ChatFormatting.GREEN);
                case "seen" -> Component.literal("Seen: 12 times").withStyle(ChatFormatting.GRAY);
                case "first_join" -> Component.literal("First joined: 14.03.2021").withStyle(ChatFormatting.GRAY);
                case "profile" -> Component.literal("Profile: Apple").withStyle(ChatFormatting.YELLOW);
                case "wealth" -> Component.literal("Bank: 42.0m | Purse: 3.2m").withStyle(ChatFormatting.GOLD);
                case "armor" -> Component.literal("Armor: Necron's Armor").withStyle(ChatFormatting.LIGHT_PURPLE);
                case "equipment" -> Component.literal("Equipment: 4/4 equipped").withStyle(ChatFormatting.BLUE);
                case "pet" -> Component.literal("Active pet: Ender Dragon").withStyle(ChatFormatting.LIGHT_PURPLE);
                case "skills" -> Component.literal("Skills: 48.7 avg").withStyle(ChatFormatting.AQUA);
                case "uuid" -> Component.literal("UUID: 466d01f5...").withStyle(ChatFormatting.DARK_GRAY);
                case "scamscreener" -> Component.translatable("marketguard.hud.scamscreener.no_entry").withStyle(ChatFormatting.GRAY);
                case "data" -> Component.literal("Data: live").withStyle(ChatFormatting.GREEN);
                case "unavailable" -> Component.literal("Unavailable: museum").withStyle(ChatFormatting.GRAY);
                default -> Component.literal(row);
            };
            case MINION_PROFIT -> switch (row) {
                case "title" -> Component.literal("Minion Profit").withStyle(ChatFormatting.AQUA);
                case "held_coins" -> Component.literal("Held Coins: 125.4k coins").withStyle(ChatFormatting.GOLD);
                case "profit" -> Component.literal("Potential Bazaar profit: 2.8m coins").withStyle(ChatFormatting.GOLD);
                case "missing" -> Component.literal("1 stack is missing a Bazaar price.").withStyle(ChatFormatting.GRAY);
                case "loading" -> Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY);
                case "unavailable" -> Component.literal("Bazaar prices unavailable.").withStyle(ChatFormatting.RED);
                case "stale" -> Component.literal("Bazaar prices may be outdated.").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case FORGE_PROFIT -> switch (row) {
                case "title" -> Component.literal("Forge Bazaar Profit").withStyle(ChatFormatting.AQUA);
                case "profit" -> Component.literal("Potential Bazaar profit: 4.6m").withStyle(ChatFormatting.GOLD);
                case "missing" -> Component.literal("1 stack is missing a Bazaar price.").withStyle(ChatFormatting.GRAY);
                case "loading" -> Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY);
                case "unavailable" -> Component.literal("Bazaar prices are unavailable.").withStyle(ChatFormatting.RED);
                case "stale" -> Component.literal("Bazaar prices may be outdated.").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case PROFIT_TRACKER -> switch (row) {
                case "title" -> Component.literal("Profit Tracker").withStyle(ChatFormatting.AQUA);
                case "bazaar" -> Component.literal("Bazaar: +2.4m").withStyle(ChatFormatting.GREEN);
                case "auction_house" -> Component.literal("Auction House: +8.1m").withStyle(ChatFormatting.GREEN);
                case "minion" -> Component.literal("Minion: +1.7m").withStyle(ChatFormatting.GREEN);
                case "interest" -> Component.literal("Interest: +250.0k").withStyle(ChatFormatting.GREEN);
                case "allowance" -> Component.literal("Allowance: +50.0k").withStyle(ChatFormatting.GREEN);
                case "total" -> Component.literal("Total: +12.5m").withStyle(ChatFormatting.GOLD);
                default -> Component.literal(row);
            };
        };
    }

    public static void reset(HudId hud) {
        screens(hud).clear();
        screens(hud).addAll(defaultScreens(hud));
        rowValues(hud).clear();
        rowValues(hud).addAll(defaultRows(hud));
        MarketGuardConfig.save();
    }

    public static void addSettingsButtons(String category, MidnightConfigListWidget list, MidnightConfigScreen screen) {
        if (!"hud".equals(category)) {
            return;
        }

        var entryInfo = MarketGuardConfig.hudEntryInfo();
        int controlsX = list.getWidth() - 185;
        for (HudId hud : HudId.values()) {
            list.addButton(List.of(Button.builder(
                    Component.translatable("marketguard.hud.edit"),
                    button -> HudEditorScreen.open(screen, hud)
            ).bounds(controlsX, 0, 150, 20).build()),
                    Component.translatable("marketguard.hud." + hud.key()), entryInfo);
        }
    }

    static Component visibilityLabel(boolean visible) {
        return Component.translatable(visible ? "marketguard.hud.visible" : "marketguard.hud.hidden");
    }

    private static List<String> screens(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.auctionPriceHudScreens;
            case PLAYER -> MarketGuardConfig.playerHudScreens;
            case MINION_PROFIT -> MarketGuardConfig.minionProfitHudScreens;
            case FORGE_PROFIT -> MarketGuardConfig.forgeProfitHudScreens;
            case PROFIT_TRACKER -> MarketGuardConfig.profitTrackerHudScreens;
        };
    }

    private static List<String> rowValues(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.auctionPriceHudRows;
            case PLAYER -> MarketGuardConfig.playerHudRows;
            case MINION_PROFIT -> MarketGuardConfig.minionProfitHudRows;
            case FORGE_PROFIT -> MarketGuardConfig.forgeProfitHudRows;
            case PROFIT_TRACKER -> MarketGuardConfig.profitTrackerHudRows;
        };
    }

    private static List<String> defaultScreens(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> List.of("bin_view");
            case PLAYER -> List.of("trade", "profile", "bin_view");
            case MINION_PROFIT -> List.of("minion");
            case FORGE_PROFIT -> List.of("forge");
            case PROFIT_TRACKER -> List.of("ingame");
        };
    }

    private static List<String> defaultRows(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> List.of("title", "item", "auction", "lowest_bin", "difference", "advice", "stale");
            case PLAYER -> List.of("title", "name", "status", "seen", "first_join", "profile", "wealth", "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable");
            case MINION_PROFIT -> List.of("title", "held_coins", "profit", "missing", "loading", "unavailable", "stale");
            case FORGE_PROFIT -> List.of("title", "profit", "missing", "loading", "unavailable", "stale");
            case PROFIT_TRACKER -> List.of("title", "bazaar", "auction_house", "minion", "interest", "allowance", "total");
        };
    }

    private static List<String> availableRows(HudId hud) {
        if (hud != HudId.PLAYER) {
            return defaultRows(hud);
        }

        return switch (MarketGuardConfig.getPlayerHudPreset()) {
            case "trade" -> List.of("title", "name", "status", "seen", "scamscreener");
            case "compact" -> List.of("title", "name", "status", "seen", "wealth", "scamscreener", "data", "unavailable");
            case "profile" -> List.of("title", "name", "status", "seen", "first_join", "profile", "wealth", "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable");
            case "all" -> defaultRows(hud);
            default -> defaultRows(hud);
        };
    }
}
