package eu.tango.scamscreener.marketguard.profittracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProfileResolver {
    private static final Pattern PROFILE_PATTERN = Pattern.compile("(?i)profile\\s*:?\\s*([A-Za-z0-9_ ]+)");

    private ProfileResolver() {}

    static String resolveCurrentProfileId(MinecraftClient client) {
        if (client == null || client.world == null) {
            return null;
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        if (scoreboard == null) {
            return null;
        }

        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return null;
        }

        List<ScoreboardEntry> entries = new ArrayList<>(scoreboard.getScoreboardEntries(sidebar));
        entries.sort(Comparator.comparingInt(ScoreboardEntry::value).reversed());

        for (ScoreboardEntry entry : entries) {
            if (entry.hidden()) {
                continue;
            }

            String profileId = extractProfile(entry.name());
            if (profileId == null && entry.display() != null) {
                profileId = extractProfile(entry.display());
            }
            if (profileId != null) {
                return profileId;
            }
        }

        return null;
    }

    private static String extractProfile(Text text) {
        if (text == null) {
            return null;
        }

        String raw = text.getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Matcher matcher = PROFILE_PATTERN.matcher(raw.replaceAll("\\s+", " ").trim());
        if (!matcher.find()) {
            return null;
        }

        String profile = matcher.group(1);
        if (profile == null || profile.isBlank()) {
            return null;
        }

        return profile.trim().toLowerCase(Locale.ROOT);
    }
}
