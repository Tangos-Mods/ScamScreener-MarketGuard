package eu.tango.scamscreener.marketguard.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;

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

}
