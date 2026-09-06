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
        TRADE_GUARD("tradeGuard"),
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
                case "item" -> Component.literal("6th Anniversary Barn Skin").withStyle(ChatFormatting.WHITE);
                case "auction" -> Component.literal("This auction: 15.0m").withStyle(ChatFormatting.GOLD);
                case "lowest_bin" -> Component.literal("Reference: 15.4m (high quality, 3 signals)").withStyle(ChatFormatting.GREEN);
                case "difference" -> Component.literal("Difference to reference: -400.0k (-2.6%)").withStyle(ChatFormatting.GREEN);
                case "advice" -> Component.literal("Price is near the reference.").withStyle(ChatFormatting.GRAY);
                case "volatility" -> Component.literal("Price volatility: 7d avg is 20.0% above 30d avg.").withStyle(ChatFormatting.YELLOW);
                case "liquidity" -> Component.literal("Bazaar liquidity risk: 400 units on thinner book side.").withStyle(ChatFormatting.YELLOW);
                case "stale" -> Component.literal("Price data may be outdated.").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case PLAYER -> switch (row) {
                case "name" -> Component.literal("Name: TangoDev").withStyle(ChatFormatting.WHITE);
                case "status" -> Component.literal("Status: available").withStyle(ChatFormatting.GREEN);
                case "seen" -> Component.literal("Seen: 12 times").withStyle(ChatFormatting.GRAY);
                case "first_join" -> Component.literal("First joined: 14.03.2021").withStyle(ChatFormatting.GRAY);
                case "profile" -> Component.literal("Profile: Apple").withStyle(ChatFormatting.YELLOW);
                case "wealth" -> Component.literal("Bank: 42.0m | Purse: 3.2m").withStyle(ChatFormatting.GOLD);
                case "profile_value" -> Component.literal("Known profile value estimate: 231.4m coins").withStyle(ChatFormatting.GOLD);
                case "value_coverage" -> Component.literal("Priced visible gear: 8/8 items").withStyle(ChatFormatting.GREEN);
                case "value_missing" -> Component.literal("Not included: inventory, pets").withStyle(ChatFormatting.DARK_GRAY);
                case "value_status" -> Component.literal("Estimate data: finance API + cached market prices").withStyle(ChatFormatting.DARK_GRAY);
                case "museum" -> Component.literal("Museum: Value: 185.0m | Appraisal: available").withStyle(ChatFormatting.GOLD);
                case "museum_items" -> Component.literal("Museum ownership: 143 donated exhibits | 6 special exhibits").withStyle(ChatFormatting.GRAY);
                case "finance_status" -> Component.literal("Finance & museum: ok").withStyle(ChatFormatting.DARK_GRAY);
                case "finance_history" -> Component.literal("Income, costs & ROI: unavailable").withStyle(ChatFormatting.DARK_GRAY);
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
            case TRADE_GUARD -> switch (row) {
                case "own_value" -> Component.literal("Your item value: 18.0m coins").withStyle(ChatFormatting.GOLD);
                case "partner_value" -> Component.literal("Their item value: 13.5m coins").withStyle(ChatFormatting.GOLD);
                case "difference" -> Component.literal("You give 4.5m coins more in items.").withStyle(ChatFormatting.YELLOW);
                case "unpriced" -> Component.literal("Unpriced item stacks — yours: 0, theirs: 1").withStyle(ChatFormatting.YELLOW);
                case "data" -> Component.literal("Data: partial item values; coin offers are not included.").withStyle(ChatFormatting.YELLOW);
                case "warning" -> Component.literal("Warning: item-side disadvantage 4.5m coins (25.0%).").withStyle(ChatFormatting.RED);
                default -> Component.literal(row);
            };
            case MINION_PROFIT -> switch (row) {
                case "held_coins" -> Component.literal("Held Coins: 125.4k coins").withStyle(ChatFormatting.GOLD);
                case "profit" -> Component.literal("Potential Bazaar profit: 2.8m coins").withStyle(ChatFormatting.GOLD);
                case "forecast" -> Component.literal("Observed rate: 165.0k coins/h · 24h: 4.0m coins").withStyle(ChatFormatting.GREEN);
                case "forecast_status" -> Component.literal("Basis: visible increase over 3m 20s.").withStyle(ChatFormatting.DARK_GRAY);
                case "missing" -> Component.literal("1 stack is missing a Bazaar price.").withStyle(ChatFormatting.GRAY);
                case "loading" -> Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY);
                case "unavailable" -> Component.literal("Bazaar prices unavailable.").withStyle(ChatFormatting.RED);
                case "stale" -> Component.literal("Bazaar prices may be outdated.").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case FORGE_PROFIT -> switch (row) {
                case "profit" -> Component.literal("Potential Bazaar profit: 4.6m").withStyle(ChatFormatting.GOLD);
                case "missing" -> Component.literal("1 stack is missing a Bazaar price.").withStyle(ChatFormatting.GRAY);
                case "loading" -> Component.literal("Loading Bazaar prices...").withStyle(ChatFormatting.GRAY);
                case "unavailable" -> Component.literal("Bazaar prices are unavailable.").withStyle(ChatFormatting.RED);
                case "stale" -> Component.literal("Bazaar prices may be outdated.").withStyle(ChatFormatting.YELLOW);
                default -> Component.literal(row);
            };
            case PROFIT_TRACKER -> switch (row) {
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
            case TRADE_GUARD -> MarketGuardConfig.tradeGuardHudScreens;
            case MINION_PROFIT -> MarketGuardConfig.minionProfitHudScreens;
            case FORGE_PROFIT -> MarketGuardConfig.forgeProfitHudScreens;
            case PROFIT_TRACKER -> MarketGuardConfig.profitTrackerHudScreens;
        };
    }

    private static List<String> rowValues(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> MarketGuardConfig.auctionPriceHudRows;
            case PLAYER -> MarketGuardConfig.playerHudRows;
            case TRADE_GUARD -> MarketGuardConfig.tradeGuardHudRows;
            case MINION_PROFIT -> MarketGuardConfig.minionProfitHudRows;
            case FORGE_PROFIT -> MarketGuardConfig.forgeProfitHudRows;
            case PROFIT_TRACKER -> MarketGuardConfig.profitTrackerHudRows;
        };
    }

    private static List<String> defaultScreens(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> List.of("bin_view");
            case PLAYER -> List.of("trade", "profile", "bin_view");
            case TRADE_GUARD -> List.of("trade");
            case MINION_PROFIT -> List.of("minion");
            case FORGE_PROFIT -> List.of("forge");
            case PROFIT_TRACKER -> List.of("ingame");
        };
    }

    private static List<String> defaultRows(HudId hud) {
        return switch (hud) {
            case AUCTION_PRICE -> List.of("item", "auction", "lowest_bin", "difference", "advice", "volatility", "liquidity", "stale");
            case PLAYER -> List.of(
                    "name", "status", "seen", "first_join", "profile", "wealth",
                    "profile_value", "value_coverage", "value_missing", "value_status",
                    "museum", "museum_items", "finance_status", "finance_history",
                    "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable"
            );
            case TRADE_GUARD -> List.of("own_value", "partner_value", "difference", "unpriced", "data", "warning");
            case MINION_PROFIT -> List.of("held_coins", "profit", "forecast", "forecast_status", "missing", "loading", "unavailable", "stale");
            case FORGE_PROFIT -> List.of("profit", "missing", "loading", "unavailable", "stale");
            case PROFIT_TRACKER -> List.of("bazaar", "auction_house", "minion", "interest", "allowance", "total");
        };
    }

    private static List<String> availableRows(HudId hud) {
        if (hud != HudId.PLAYER) {
            return defaultRows(hud);
        }

        return switch (MarketGuardConfig.getPlayerHudPreset()) {
            case "trade" -> List.of("name", "status", "seen", "scamscreener");
            case "compact" -> List.of(
                    "name", "status", "seen", "wealth", "profile_value", "value_coverage", "value_status",
                    "finance_status", "scamscreener", "data", "unavailable"
            );
            case "profile" -> List.of(
                    "name", "status", "seen", "first_join", "profile", "wealth",
                    "profile_value", "value_coverage", "value_missing", "value_status",
                    "museum", "museum_items", "finance_status", "finance_history",
                    "armor", "equipment", "pet", "skills", "uuid", "scamscreener", "data", "unavailable"
            );
            case "all" -> defaultRows(hud);
            default -> defaultRows(hud);
        };
    }
}
