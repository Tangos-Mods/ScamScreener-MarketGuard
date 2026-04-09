package eu.tango.scamscreener.marketguard.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public final class MarketGuardCommand {

    private MarketGuardCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var marketguard = dispatcher.register(ClientCommandManager.literal("marketguard")
                    .executes(context -> sendStatus(context.getSource()))
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
        AuctionUnderbidding.setThreshold(value);

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
        AuctionOverbidding.setThreshold(value);

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
        ).formatted(Formatting.GRAY)));
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
}
