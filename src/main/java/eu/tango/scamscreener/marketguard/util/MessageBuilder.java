package eu.tango.scamscreener.marketguard.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.util.Locale;

public class MessageBuilder {

    public static final Text PREFIX = Text.empty()
            .append(Text.literal("[MarketGuard]").withColor(Colors.LIGHT_RED))
            .append(Text.literal(" ").formatted(Formatting.GRAY));

    public static void error(Text error, ClientPlayerEntity player) {
         player.sendMessage(PREFIX.copy().append(error), false);
    }

    public static void underbidding(String item, double underbidPercent, int remainingClicks, ClientPlayerEntity player) {
        int color = Colors.YELLOW;

        if (item.contains(";")) {
           String[] s = item.split(";", 2);
           item = s[0];
           try {
               ItemTier tier = ItemTier.fromId(Integer.parseInt(s[1]));
               if (tier != null) { color = tier.getColor(); }
           } catch (NumberFormatException ignored) {}

        }

        player.sendMessage(PREFIX.copy()
                .append(Text.literal("You are underbidding ").formatted(Formatting.GRAY))
                .append(Text.literal(firstLetterUp(item)).withColor(color))
                .append(Text.literal(" by ").formatted(Formatting.GRAY))
                .append(Text.literal(String.format(Locale.US, "%.2f%%", underbidPercent)).withColor(Colors.YELLOW))
                .append(Text.literal(" (").formatted(Formatting.GRAY))
                .append(Text.literal(String.valueOf(remainingClicks) + " clicks").formatted(Formatting.BOLD, Formatting.RED))
                .append(Text.literal(" until bypass)").formatted(Formatting.GRAY)),
                false);
    }

    public static void overbidding(String item, double overbidPercent, int remainingClicks, ClientPlayerEntity player) {
        int color = Colors.YELLOW;

        if (item.contains(";")) {
            String[] s = item.split(";", 2);
            item = s[0];
            try {
                ItemTier tier = ItemTier.fromId(Integer.parseInt(s[1]));
                if (tier != null) { color = tier.getColor(); }
            } catch (NumberFormatException ignored) {}

        }

        player.sendMessage(PREFIX.copy()
                        .append(Text.literal("You are overbidding ").formatted(Formatting.GRAY))
                        .append(Text.literal(firstLetterUp(item)).withColor(color))
                        .append(Text.literal(" by ").formatted(Formatting.GRAY))
                        .append(Text.literal(String.format(Locale.US, "%.2f%%", overbidPercent)).withColor(Colors.YELLOW))
                        .append(Text.literal(" (").formatted(Formatting.GRAY))
                        .append(Text.literal(String.valueOf(remainingClicks) + " clicks").formatted(Formatting.BOLD, Formatting.RED))
                        .append(Text.literal(" until bypass)").formatted(Formatting.GRAY)),
                false);
    }

    public static void blacklistedPlayer(String playerName, ClientPlayerEntity player) {
        String displayName = (playerName == null || playerName.isBlank()) ? "<unknown player>" : playerName;
        player.sendMessage(
                PREFIX.copy().append(Text.literal(displayName + " is listed in your blacklist! Be cautious!").formatted(Formatting.YELLOW)),
                false
        );
    }

    public static MutableText updateAvailable(String currentVersion, String latestVersion, String modrinthUrl, String changelog) {
        return PREFIX.copy()
                .append(Text.literal("Update available ").formatted(Formatting.GRAY))
                .append(Text.literal(displayVersionOnly(currentVersion)).formatted(Formatting.YELLOW))
                .append(Text.literal(" -> ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(displayVersionOnly(latestVersion)).formatted(Formatting.GREEN, Formatting.BOLD))
                .append(Text.literal(". ").formatted(Formatting.GRAY))
                .append(urlActionTag("Click", Formatting.YELLOW, changelogHoverText(changelog), modrinthUrl))
                .append(Text.literal(" to open on Modrinth.").formatted(Formatting.GRAY));
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

    static MutableText changelogHoverText(String changelog) {
        String normalized = changelog == null ? "" : changelog.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);
        int lineCount = rawLines.length;
        while (lineCount > 0 && rawLines[lineCount - 1].isBlank()) {
            lineCount--;
        }

        if (lineCount == 0) {
            return Text.literal("No changelog available.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC);
        }

        MutableText hover = Text.literal("");
        int previewLines = Math.min(10, lineCount);
        for (int index = 0; index < previewLines; index++) {
            if (index > 0) {
                hover.append(Text.literal("\n"));
            }
            hover.append(Text.literal(rawLines[index]).formatted(Formatting.GRAY));
        }
        if (lineCount > previewLines) {
            hover.append(Text.literal("\n"));
            hover.append(Text.literal("and more...").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        }

        return hover;
    }

    private static MutableText urlActionTag(String label, Formatting color, Text hover, String url) {
        Style style = Style.EMPTY.withColor(color);
        if (hover != null) {
            style = style.withHoverEvent(new HoverEvent.ShowText(hover));
        }
        if (url != null && !url.isBlank()) {
            style = style.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)));
        } else {
            style = style.withStrikethrough(true);
        }

        return Text.literal("[" + label + "]").setStyle(style);
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
