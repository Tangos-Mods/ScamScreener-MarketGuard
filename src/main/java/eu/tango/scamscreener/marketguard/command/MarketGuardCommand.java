package eu.tango.scamscreener.marketguard.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.midnightdust.lib.config.MidnightConfig;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;
import eu.tango.scamscreener.marketguard.hud.ProfitTrackerHud;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.tangosHudLib.api.HudLibrary;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;

public final class MarketGuardCommand {
    private static int settingsOpenDelayTicks = -1;

    private MarketGuardCommand() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (settingsOpenDelayTicks < 0) {
                return;
            }
            if (settingsOpenDelayTicks-- > 0) {
                return;
            }

            settingsOpenDelayTicks = -1;
            //? if >=26.2 {
            client.gui.setScreen(MidnightConfig.getScreen(client.gui.screen(), MarketGuard.MOD_ID));
            //?} else {
            /*client.setScreen(MidnightConfig.getScreen(client.screen, MarketGuard.MOD_ID));*/
            //?}
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var playerHud = ClientCommands.literal("playerhud")
                    .then(ClientCommands.literal("clear")
                            .executes(context -> clearPlayerHud(context.getSource())))
                    .then(ClientCommands.literal("preset")
                            .then(ClientCommands.literal("trade")
                                    .executes(context -> setPlayerHudPreset(context.getSource(), "trade")))
                            .then(ClientCommands.literal("compact")
                                    .executes(context -> setPlayerHudPreset(context.getSource(), "compact")))
                            .then(ClientCommands.literal("profile")
                                    .executes(context -> setPlayerHudPreset(context.getSource(), "profile")))
                            .then(ClientCommands.literal("all")
                                    .executes(context -> setPlayerHudPreset(context.getSource(), "all"))))
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                            .executes(context -> showPlayerHud(context.getSource(), StringArgumentType.getString(context, "player"), null))
                            .then(ClientCommands.argument("profileId", StringArgumentType.word())
                                    .executes(context -> showPlayerHud(
                                            context.getSource(),
                                            StringArgumentType.getString(context, "player"),
                                            StringArgumentType.getString(context, "profileId")
                                    ))));
            var hudLayout = ClientCommands.literal("hudlayout")
                    .then(ClientCommands.literal("save")
                            .then(ClientCommands.argument("name", StringArgumentType.word())
                                    .executes(context -> saveHudLayout(context.getSource(), StringArgumentType.getString(context, "name")))))
                    .then(ClientCommands.literal("load")
                            .then(ClientCommands.argument("name", StringArgumentType.word())
                                    .executes(context -> loadHudLayout(context.getSource(), StringArgumentType.getString(context, "name")))));

            var marketguard = dispatcher.register(ClientCommands.literal("marketguard")
                    .executes(context -> openSettings(context.getSource()))
                    .then(ClientCommands.literal("reset")
                            .executes(context -> reset(context.getSource())))
                    .then(ClientCommands.literal("reload")
                            .executes(context -> reload(context.getSource())))
                    .then(ClientCommands.literal("debug")
                            .executes(context -> toggleDebug(context.getSource())))
                    .then(ClientCommands.literal("numberformat")
                            .executes(context -> toggleNumberFormat(context.getSource())))
                    .then(ClientCommands.literal("threshold")
                            .then(ClientCommands.argument("value", LongArgumentType.longArg(0L))
                                    .executes(context -> setAbsoluteThreshold(context, getLong(context, "value")))))
                    .then(ClientCommands.literal("underbidding")
                            .then(ClientCommands.argument("value", IntegerArgumentType.integer(0, 100))
                                    .executes(context -> setUnderbidding(context, getInteger(context, "value")))))
                    .then(ClientCommands.literal("overbidding")
                            .then(ClientCommands.argument("value", IntegerArgumentType.integer(100))
                                    .executes(context -> setOverbidding(context, getInteger(context, "value")))))
                    .then(ClientCommands.literal("profit")
                            .executes(context -> toggleProfitTrackerHud(context.getSource())))
                    .then(ClientCommands.literal("profittracker")
                            .executes(context -> toggleProfitTrackerHud(context.getSource())))
                    .then(hudLayout)
                    .then(playerHud));

            dispatcher.register(ClientCommands.literal("mg")
                    .executes(context -> openSettings(context.getSource()))
                    .redirect(marketguard));
        });
    }

    private static int openSettings(FabricClientCommandSource source) {
        settingsOpenDelayTicks = 1;
        return 1;
    }

    private static int setUnderbidding(CommandContext<FabricClientCommandSource> context, int value) {
        int previousThreshold = AuctionUnderbidding.getThreshold();
        AuctionUnderbidding.setThreshold(value);
        if (!MarketGuardConfig.save()) {
            AuctionUnderbidding.setThreshold(previousThreshold);
            context.getSource().sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        if (value == 0 || value == 100) {
            context.getSource().sendFeedback(message(Component.literal("Underbidding protection disabled.").withStyle(ChatFormatting.YELLOW)));
            return 1;
        }

        context.getSource().sendFeedback(message(Component.literal(
                "Underbidding threshold set to " + value + "% (max " + (100 - value) + "% under Lowest BIN)."
        ).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int setOverbidding(CommandContext<FabricClientCommandSource> context, int value) {
        int previousThreshold = AuctionOverbidding.getThreshold();
        AuctionOverbidding.setThreshold(value);
        if (!MarketGuardConfig.save()) {
            AuctionOverbidding.setThreshold(previousThreshold);
            context.getSource().sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        if (value == 100) {
            context.getSource().sendFeedback(message(Component.literal("Overbidding protection disabled.").withStyle(ChatFormatting.YELLOW)));
            return 1;
        }

        context.getSource().sendFeedback(message(Component.literal(
                "Overbidding threshold set to " + value + "% (max " + (value - 100) + "% above Lowest BIN)."
        ).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int sendStatus(FabricClientCommandSource source) {
        source.sendFeedback(message(Component.literal(
                "Underbidding: " + formatUnderbiddingThreshold(AuctionUnderbidding.getThreshold())
                        + " | Overbidding: " + formatOverbiddingThreshold(AuctionOverbidding.getThreshold())
                        + " | Absolute threshold: " + formatPrice(MarketGuardConfig.getAbsoluteThreshold())
                        + " | Debug: " + (MarketGuardConfig.isDebugEnabled() ? "enabled" : "disabled")
        ).withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int reset(FabricClientCommandSource source) {
        int previousUnderbiddingThreshold = AuctionUnderbidding.getThreshold();
        int previousOverbiddingThreshold = AuctionOverbidding.getThreshold();
        long previousAbsoluteThreshold = MarketGuardConfig.getAbsoluteThreshold();

        AuctionUnderbidding.setThreshold(AuctionUnderbidding.DEFAULT_THRESHOLD);
        AuctionOverbidding.setThreshold(AuctionOverbidding.DEFAULT_THRESHOLD);
        MarketGuardConfig.setAbsoluteThreshold(MarketGuardConfig.DEFAULT_ABSOLUTE_THRESHOLD);
        if (!MarketGuardConfig.save()) {
            AuctionUnderbidding.setThreshold(previousUnderbiddingThreshold);
            AuctionOverbidding.setThreshold(previousOverbiddingThreshold);
            MarketGuardConfig.setAbsoluteThreshold(previousAbsoluteThreshold);
            source.sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Component.literal(
                "Thresholds reset to defaults. Underbidding: "
                        + formatUnderbiddingThreshold(AuctionUnderbidding.getThreshold())
                        + " | Overbidding: " + formatOverbiddingThreshold(AuctionOverbidding.getThreshold())
                        + " | Absolute threshold: " + formatPrice(MarketGuardConfig.getAbsoluteThreshold())
        ).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int reload(FabricClientCommandSource source) {
        MarketGuardConfig.load();
        source.sendFeedback(message(Component.literal("Reloaded marketguard/config.json.").withStyle(ChatFormatting.GREEN)));
        return sendStatus(source);
    }

    private static int toggleDebug(FabricClientCommandSource source) {
        boolean previousDebugEnabled = MarketGuardConfig.isDebugEnabled();
        boolean nextDebugEnabled = !previousDebugEnabled;
        MarketGuardConfig.setDebugEnabled(nextDebugEnabled);
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setDebugEnabled(previousDebugEnabled);
            source.sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Component.literal(
                "Debug logging " + (nextDebugEnabled ? "enabled" : "disabled") + "."
        ).withStyle(nextDebugEnabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        return 1;
    }

    private static int toggleNumberFormat(FabricClientCommandSource source) {
        boolean nextShortFormat = !MarketGuardConfig.isShortNumberFormat();
        MarketGuardConfig.setShortNumberFormat(nextShortFormat);
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setShortNumberFormat(!nextShortFormat);
            source.sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Component.literal(
                "HUD number format set to " + (nextShortFormat ? "short (1k, 1M, 1B)." : "full (1,000, 1,000,000).")
        ).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int setAbsoluteThreshold(CommandContext<FabricClientCommandSource> context, long value) {
        long previousThreshold = MarketGuardConfig.getAbsoluteThreshold();
        MarketGuardConfig.setAbsoluteThreshold(value);
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setAbsoluteThreshold(previousThreshold);
            context.getSource().sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        context.getSource().sendFeedback(message(Component.literal(
                "Absolute threshold set to " + formatPrice(value) + " coins."
        ).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int showPlayerHud(FabricClientCommandSource source, String player, String profileId) {
        PlayerHud.show(player, profileId);
        source.sendFeedback(message(Component.literal(
                profileId == null
                        ? "Loading the selected SkyBlock profile for " + player + "."
                        : "Loading profile " + profileId + " for " + player + "."
        ).withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int clearPlayerHud(FabricClientCommandSource source) {
        PlayerHud.clear();
        source.sendFeedback(message(Component.literal("Player HUD hidden.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int setPlayerHudPreset(FabricClientCommandSource source, String preset) {
        String previousPreset = MarketGuardConfig.getPlayerHudPreset();
        try {
            MarketGuardConfig.setPlayerHudPreset(preset);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setPlayerHudPreset(previousPreset);
            source.sendFeedback(message(Component.literal("Failed to save player HUD preset.").withStyle(ChatFormatting.RED)));
            return 0;
        }
        PlayerHud.setPreset(preset);
        source.sendFeedback(message(Component.literal("Player HUD preset set to " + preset + ".").withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int saveHudLayout(FabricClientCommandSource source, String name) {
        if (!HudLibrary.saveLayout(MarketGuard.MOD_ID, name)) {
            source.sendFeedback(message(Component.literal("Failed to save HUD layout '" + name + "'.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Component.literal("Saved HUD layout '" + name + "'.").withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int loadHudLayout(FabricClientCommandSource source, String name) {
        if (!HudLibrary.loadLayout(MarketGuard.MOD_ID, name)) {
            source.sendFeedback(message(Component.literal("HUD layout '" + name + "' was not found.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Component.literal("Loaded HUD layout '" + name + "'.").withStyle(ChatFormatting.GREEN)));
        return 1;
    }

    private static int toggleProfitTrackerHud(FabricClientCommandSource source) {
        boolean nextEnabled = !ProfitTrackerHud.isDisplayEnabled();
        if (!ProfitTrackerHud.setDisplayEnabled(nextEnabled)) {
            source.sendFeedback(message(Component.literal("Failed to save marketguard/config.json.").withStyle(ChatFormatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Component.literal(
                "Profit tracker display " + (nextEnabled ? "enabled." : "hidden.")
        ).withStyle(nextEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        return 1;
    }

    private static String formatUnderbiddingThreshold(int threshold) {
        if (threshold == 0 || threshold == 100) {
            return "disabled";
        }

        return threshold + "% (max " + (100 - threshold) + "% under Lowest BIN)";
    }

    private static String formatOverbiddingThreshold(int threshold) {
        if (threshold == 100) {
            return "disabled";
        }

        return threshold + "% (max " + (threshold - 100) + "% above Lowest BIN)";
    }

    private static Component message(Component text) {
        return MessageBuilder.PREFIX.copy().append(text);
    }

    private static String formatPrice(double price) {
        if (Math.abs(price - Math.rint(price)) < 0.005) {
            return String.format(java.util.Locale.US, "%,.0f", price);
        }

        return String.format(java.util.Locale.US, "%,.2f", price);
    }
}
