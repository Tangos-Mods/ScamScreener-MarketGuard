package eu.tango.scamscreener.marketguard.profittracker;

import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProfitTrackerResetScreen {
    private ProfitTrackerResetScreen() {}

    public static void open(Screen parent) {
        Minecraft client = Minecraft.getInstance();
        ConfirmScreen confirmation = new ConfirmScreen(confirmed -> {
            if (confirmed && client.player != null) {
                boolean reset = ProfitTracker.resetAll();
                client.player.sendSystemMessage(MessageBuilder.PREFIX.copy().append(
                        Component.translatable(reset
                                        ? "marketguard.profit_tracker.reset.success"
                                        : "marketguard.profit_tracker.reset.failed")
                                .withStyle(reset ? ChatFormatting.GREEN : ChatFormatting.RED)
                ));
            } else if (confirmed) {
                ProfitTracker.resetAll();
            }
            setScreen(client, parent);
        }, Component.translatable("marketguard.profit_tracker.reset.confirm_title"),
                Component.translatable("marketguard.profit_tracker.reset.confirm_message"),
                Component.translatable("marketguard.profit_tracker.reset").withStyle(ChatFormatting.RED),
                Component.translatable("gui.cancel"));
        setScreen(client, confirmation);
    }

    private static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else {
        /*client.setScreen(screen);*/
        //?}
    }
}
