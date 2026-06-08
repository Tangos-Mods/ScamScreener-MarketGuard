package eu.tango.scamscreener.marketguard.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.util.Locale;

public class MessageBuilder {
    private static final int LIGHT_RED = 0xFF5555;
    private static final int YELLOW = 0xFFFF55;

    public static final Component PREFIX = Component.empty()
            .append(Component.literal("[MarketGuard]").withColor(LIGHT_RED))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY));

    public static void error(Component error, LocalPlayer player) {
         player.sendSystemMessage(PREFIX.copy().append(error));
    }

    public static void underbidding(
            String itemId,
            String itemDisplayName,
            double underbidPercent,
            double lowestPossiblePrice,
            int remainingClicks,
            LocalPlayer player
    ) {
        player.sendSystemMessage(buildUnderbiddingMessage(itemId, itemDisplayName, underbidPercent, lowestPossiblePrice, remainingClicks));
    }

    static MutableComponent buildUnderbiddingMessage(
            String itemId,
            String itemDisplayName,
            double underbidPercent,
            double lowestPossiblePrice,
            int remainingClicks
    ) {
        return buildAuctionProtectionMessage(
                "You are underbidding ",
                ". Lowest possible price is ",
                itemId,
                itemDisplayName,
                underbidPercent,
                lowestPossiblePrice,
                remainingClicks
        );
    }

    public static void overbidding(
            String itemId,
            String itemDisplayName,
            double overbidPercent,
            double highestPossiblePrice,
            int remainingClicks,
            LocalPlayer player
    ) {
        player.sendSystemMessage(buildOverbiddingMessage(itemId, itemDisplayName, overbidPercent, highestPossiblePrice, remainingClicks));
    }

    static MutableComponent buildOverbiddingMessage(
            String itemId,
            String itemDisplayName,
            double overbidPercent,
            double highestPossiblePrice,
            int remainingClicks
    ) {
        return buildAuctionProtectionMessage(
                "You are overbidding ",
                ". Highest possible price is ",
                itemId,
                itemDisplayName,
                overbidPercent,
                highestPossiblePrice,
                remainingClicks
        );
    }

    private static MutableComponent buildAuctionProtectionMessage(
            String actionText,
            String priceText,
            String itemId,
            String itemDisplayName,
            double deviationPercent,
            double limitPrice,
            int remainingClicks
    ) {
        int color = resolveItemColor(itemId);

        return PREFIX.copy()
                .append(Component.literal(actionText).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(resolveDisplayItemName(itemDisplayName, itemId)).withColor(color))
                .append(Component.literal(" by ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format(Locale.US, "%.2f%%", deviationPercent)).withColor(YELLOW))
                .append(Component.literal(priceText).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatPrice(limitPrice)).withColor(YELLOW))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(remainingClicks + " clicks").withStyle(ChatFormatting.BOLD, ChatFormatting.RED))
                .append(Component.literal(" until bypass)").withStyle(ChatFormatting.GRAY));
    }

    public static void blacklistedPlayer(String playerName, LocalPlayer player) {
        String displayName = (playerName == null || playerName.isBlank()) ? "<unknown player>" : playerName;
        player.sendSystemMessage(
                PREFIX.copy().append(Component.literal(displayName + " is listed in your blacklist! Be cautious!").withStyle(ChatFormatting.YELLOW))
        );
    }

    static String resolveDisplayItemName(String itemDisplayName, String itemId) {
        if (itemDisplayName != null && !itemDisplayName.isBlank()) {
            return itemDisplayName;
        }

        if (itemId == null || itemId.isBlank()) {
            return "<unknown item>";
        }

        String normalizedItemId = itemId.contains(";")
                ? itemId.split(";", 2)[0]
                : itemId;
        return firstLetterUp(normalizedItemId);
    }

    public static MutableComponent updateAvailable(String currentVersion, String latestVersion, String modrinthUrl, String changelog) {
        return PREFIX.copy()
                .append(Component.literal("Update available ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(displayVersionOnly(currentVersion)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(displayVersionOnly(latestVersion)).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal(". ").withStyle(ChatFormatting.GRAY))
                .append(urlActionTag("Click", ChatFormatting.YELLOW, changelogHoverText(changelog), modrinthUrl))
                .append(Component.literal(" to open on Modrinth.").withStyle(ChatFormatting.GRAY));
    }

    private static String firstLetterUp(String s) {
        if (s == null || s.isBlank()) return s;
        String normalized = s.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        String[] words = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) result.append(word.substring(1));
        }

        return result.toString();
    }

    private static int resolveItemColor(String itemId) {
        if (itemId == null || itemId.isBlank() || !itemId.contains(";")) {
            return YELLOW;
        }

        String[] splitItemId = itemId.split(";", 2);
        try {
            ItemTier tier = ItemTier.fromId(Integer.parseInt(splitItemId[1]));
            if (tier != null) {
                return tier.getColor();
            }
        } catch (NumberFormatException ignored) {
        }

        return YELLOW;
    }

    static String formatPrice(double price) {
        if (Math.abs(price - Math.rint(price)) < 0.005) {
            return String.format(Locale.US, "%,.0f", price);
        }

        return String.format(Locale.US, "%,.2f", price);
    }

    static MutableComponent changelogHoverText(String changelog) {
        String normalized = changelog == null ? "" : changelog.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);
        int lineCount = rawLines.length;
        while (lineCount > 0 && rawLines[lineCount - 1].isBlank()) {
            lineCount--;
        }

        if (lineCount == 0) {
            return Component.literal("No changelog available.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }

        MutableComponent hover = Component.literal("");
        int previewLines = Math.min(10, lineCount);
        for (int index = 0; index < previewLines; index++) {
            if (index > 0) {
                hover.append(Component.literal("\n"));
            }
            hover.append(Component.literal(rawLines[index]).withStyle(ChatFormatting.GRAY));
        }
        if (lineCount > previewLines) {
            hover.append(Component.literal("\n"));
            hover.append(Component.literal("and more...").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        return hover;
    }

    private static MutableComponent urlActionTag(String label, ChatFormatting color, Component hover, String url) {
        Style style = Style.EMPTY.withColor(color);
        if (hover != null) {
            style = style.withHoverEvent(new HoverEvent.ShowText(hover));
        }
        if (url != null && !url.isBlank()) {
            style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)));
        } else {
            style = style.withStrikethrough(true);
        }

        return Component.literal("[" + label + "]").setStyle(style);
    }

    private static String displayVersionOnly(String version) {
        if (version == null || version.isBlank()) {
            return "<unknown>";
        }

        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1).trim();
        }

        int separator = normalized.indexOf('+');
        if (separator <= 0) {
            return normalized;
        }

        return normalized.substring(0, separator);
    }

}
