package eu.tango.scamscreener.marketguard.hud;

import com.mojang.blaze3d.platform.InputConstants;
import eu.tango.scamscreener.marketguard.MarketGuard;
import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import eu.tango.scamscreener.marketguard.profittracker.ProfileResolver;
import eu.tango.scamscreener.marketguard.profittracker.ProfitTracker;
import eu.tango.scamscreener.marketguard.util.MessageBuilder;
import eu.tango.tangosHudLib.api.HudContent;
import eu.tango.tangosHudLib.api.HudLibrary;
import eu.tango.tangosHudLib.api.HudWidget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfitTrackerHud {
    private static KeyMapping toggleKey;
    private static boolean initialized;

    private ProfitTrackerHud() {}

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "Profit Tracker Display",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
        ));
        HudLibrary.registerWidgets(MarketGuard.MOD_ID, Widgets.class);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                toggleFromKey(client);
            }
        });
    }

    public static boolean isDisplayEnabled() {
        return MarketGuardConfig.isProfitTrackerHudEnabled();
    }

    public static boolean setDisplayEnabled(boolean enabled) {
        boolean previousEnabled = MarketGuardConfig.isProfitTrackerHudEnabled();
        MarketGuardConfig.setProfitTrackerHudEnabled(enabled);
        if (MarketGuardConfig.save()) {
            return true;
        }

        MarketGuardConfig.setProfitTrackerHudEnabled(previousEnabled);
        return false;
    }

    private static void toggleFromKey(Minecraft client) {
        boolean nextEnabled = !isDisplayEnabled();
        if (!setDisplayEnabled(nextEnabled) || client.player == null) {
            return;
        }

        client.player.sendSystemMessage(MessageBuilder.PREFIX.copy().append(
                Component.literal("Profit tracker display " + (nextEnabled ? "enabled." : "hidden."))
                        .withStyle(nextEnabled ? ChatFormatting.GREEN : ChatFormatting.GRAY)
        ));
    }

    public static HudContent content(boolean playerPresent, String profileId) {
        if (!isDisplayEnabled() || !playerPresent || !ProfitTracker.isSkyBlockSessionActive()) {
            return HudContent.builder()
                    .line(Component.literal("Profit Tracker"))
                    .visible(false)
                    .build();
        }

        if (profileId == null) {
            return HudContent.builder()
                    .line(Component.literal("No SkyBlock profile detected.").withStyle(ChatFormatting.GRAY))
                    .build();
        }

        double bazaarProfit = ProfitTracker.getBazaarAllTimeProfit(profileId);
        double auctionHouseProfit = ProfitTracker.getAuctionHouseAllTimeProfit(profileId);
        double minionProfit = ProfitTracker.getMinionAllTimeProfit(profileId);
        double interestProfit = ProfitTracker.getInterestAllTimeProfit(profileId);
        double allowanceProfit = ProfitTracker.getAllowanceAllTimeProfit(profileId);
        double totalProfit = bazaarProfit + auctionHouseProfit + minionProfit + interestProfit + allowanceProfit;
        Map<String, Component> lines = new LinkedHashMap<>();
        lines.put("bazaar", profitLine("Bazaar", bazaarProfit));
        lines.put("auction_house", profitLine("Auction House", auctionHouseProfit));
        lines.put("minion", profitLine("Minion", minionProfit));
        lines.put("interest", profitLine("Interest", interestProfit));
        lines.put("allowance", profitLine("Allowance", allowanceProfit));
        lines.put("total", profitLine("Total", totalProfit));
        HudContent.Builder content = HudContent.builder();
        boolean added = false;
        for (String id : HudCustomization.rows(HudCustomization.HudId.PROFIT_TRACKER)) {
            Component line = lines.get(id);
            if (line != null) { content.line(line); added = true; }
        }
        return added ? content.build() : HudContent.builder().line(Component.literal("Profit Tracker")).visible(false).build();
    }

    private static Component profitLine(String label, double value) {
        MutableComponent line = Component.literal(label + ": ").withStyle(ChatFormatting.WHITE);
        if (value > 0.0) {
            line.append(Component.literal("+").withStyle(ChatFormatting.GREEN));
        } else if (value < 0.0) {
            line.append(Component.literal("-").withStyle(ChatFormatting.RED));
        }

        line.append(Component.literal(eu.tango.scamscreener.marketguard.util.CoinFormat.formatWithDecimals(Math.abs(value))).withStyle(ChatFormatting.GOLD));
        return line;
    }

    public static final class Widgets {
        private Widgets() {}

        @HudWidget(id = "profit_tracker")
        public static HudContent profitTracker() {
            Minecraft client = Minecraft.getInstance();
            if (!HudCustomization.visibleOnCurrentScreen(HudCustomization.HudId.PROFIT_TRACKER)) {
                return HudContent.builder().line(Component.literal("Profit Tracker")).visible(false).build();
            }
            return content(client.player != null, ProfileResolver.resolveCurrentProfileId(client));
        }
    }
}
