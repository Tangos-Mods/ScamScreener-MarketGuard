package eu.tango.scamscreener.marketguard.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;

public final class MarketGuardCommand {

    private MarketGuardCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var marketguard = dispatcher.register(ClientCommandManager.literal("marketguard")
                    .executes(context -> sendStatus(context.getSource()))
                    .then(ClientCommandManager.literal("reset")
                            .executes(context -> reset(context.getSource())))
                    .then(ClientCommandManager.literal("reload")
                            .executes(context -> reload(context.getSource())))
                    .then(ClientCommandManager.literal("debug")
                            .executes(context -> toggleDebug(context.getSource())))
                    .then(ClientCommandManager.literal("threshold")
                            .then(ClientCommandManager.argument("value", LongArgumentType.longArg(0L))
                                    .executes(context -> setAbsoluteThreshold(context, getLong(context, "value")))))
                    .then(ClientCommandManager.literal("underbidding")
                            .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(0, 100))
                                    .executes(context -> setUnderbidding(context, getInteger(context, "value")))))
                    .then(ClientCommandManager.literal("overbidding")
                            .then(ClientCommandManager.argument("value", IntegerArgumentType.integer(100))
                                    .executes(context -> setOverbidding(context, getInteger(context, "value"))))));

            dispatcher.register(ClientCommandManager.literal("mg").redirect(marketguard));
        });
    }

    private static int setUnderbidding(CommandContext<FabricClientCommandSource> context, int value) {
        int previousThreshold = AuctionUnderbidding.getThreshold();
        AuctionUnderbidding.setThreshold(value);
        if (!MarketGuardConfig.save()) {
            AuctionUnderbidding.setThreshold(previousThreshold);
            context.getSource().sendFeedback(message(Text.literal("Failed to save marketguard/config.json.").formatted(Formatting.RED)));
            return 0;
        }

        if (value == 0 || value == 100) {
            context.getSource().sendFeedback(message(Text.literal("Underbidding protection disabled.").formatted(Formatting.YELLOW)));
            return 1;
        }

        context.getSource().sendFeedback(message(Text.literal(
                "Underbidding threshold set to " + value + "% (max " + (100 - value) + "% under Lowest BIN)."
        ).formatted(Formatting.GREEN)));
        return 1;
    }

    private static int setOverbidding(CommandContext<FabricClientCommandSource> context, int value) {
        int previousThreshold = AuctionOverbidding.getThreshold();
        AuctionOverbidding.setThreshold(value);
        if (!MarketGuardConfig.save()) {
            AuctionOverbidding.setThreshold(previousThreshold);
            context.getSource().sendFeedback(message(Text.literal("Failed to save marketguard/config.json.").formatted(Formatting.RED)));
            return 0;
        }

        if (value == 100) {
            context.getSource().sendFeedback(message(Text.literal("Overbidding protection disabled.").formatted(Formatting.YELLOW)));
            return 1;
        }

        context.getSource().sendFeedback(message(Text.literal(
                "Overbidding threshold set to " + value + "% (max " + (value - 100) + "% above Lowest BIN)."
        ).formatted(Formatting.GREEN)));
        return 1;
    }

    private static int sendStatus(FabricClientCommandSource source) {
        source.sendFeedback(message(Text.literal(
                "Underbidding: " + formatUnderbiddingThreshold(AuctionUnderbidding.getThreshold())
                        + " | Overbidding: " + formatOverbiddingThreshold(AuctionOverbidding.getThreshold())
                        + " | Absolute threshold: " + formatPrice(MarketGuardConfig.getAbsoluteThreshold())
                        + " | Debug: " + (MarketGuardConfig.isDebugEnabled() ? "enabled" : "disabled")
        ).formatted(Formatting.GRAY)));
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
            source.sendFeedback(message(Text.literal("Failed to save marketguard/config.json.").formatted(Formatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Text.literal(
                "Thresholds reset to defaults. Underbidding: "
                        + formatUnderbiddingThreshold(AuctionUnderbidding.getThreshold())
                        + " | Overbidding: " + formatOverbiddingThreshold(AuctionOverbidding.getThreshold())
                        + " | Absolute threshold: " + formatPrice(MarketGuardConfig.getAbsoluteThreshold())
        ).formatted(Formatting.GREEN)));
        return 1;
    }

    private static int reload(FabricClientCommandSource source) {
        MarketGuardConfig.load();
        source.sendFeedback(message(Text.literal("Reloaded marketguard/config.json.").formatted(Formatting.GREEN)));
        return sendStatus(source);
    }

    private static int toggleDebug(FabricClientCommandSource source) {
        boolean previousDebugEnabled = MarketGuardConfig.isDebugEnabled();
        boolean nextDebugEnabled = !previousDebugEnabled;
        MarketGuardConfig.setDebugEnabled(nextDebugEnabled);
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setDebugEnabled(previousDebugEnabled);
            source.sendFeedback(message(Text.literal("Failed to save marketguard/config.json.").formatted(Formatting.RED)));
            return 0;
        }

        source.sendFeedback(message(Text.literal(
                "Debug logging " + (nextDebugEnabled ? "enabled" : "disabled") + "."
        ).formatted(nextDebugEnabled ? Formatting.GREEN : Formatting.YELLOW)));
        return 1;
    }

    private static int setAbsoluteThreshold(CommandContext<FabricClientCommandSource> context, long value) {
        long previousThreshold = MarketGuardConfig.getAbsoluteThreshold();
        MarketGuardConfig.setAbsoluteThreshold(value);
        if (!MarketGuardConfig.save()) {
            MarketGuardConfig.setAbsoluteThreshold(previousThreshold);
            context.getSource().sendFeedback(message(Text.literal("Failed to save marketguard/config.json.").formatted(Formatting.RED)));
            return 0;
        }

        context.getSource().sendFeedback(message(Text.literal(
                "Absolute threshold set to " + formatPrice(value) + " coins."
        ).formatted(Formatting.GREEN)));
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

    private static Text message(Text text) {
        return MessageBuilder.PREFIX.copy().append(text);
    }

    private static String formatPrice(double price) {
        if (Math.abs(price - Math.rint(price)) < 0.005) {
            return String.format(java.util.Locale.US, "%,.0f", price);
        }

        return String.format(java.util.Locale.US, "%,.2f", price);
    }
}
