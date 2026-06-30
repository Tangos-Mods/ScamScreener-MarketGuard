package eu.tango.scamscreener.marketguard.profittracker;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProfileResolver {
    private static final Pattern PROFILE_PATTERN = Pattern.compile("(?i)profile\\s*:?\\s*([A-Za-z0-9_ ]+)");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private ProfileResolver() {}

    static String resolveCurrentProfileId(Minecraft client) {
        if (client == null || client.level == null) {
            return null;
        }

        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null) {
            return null;
        }

        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return null;
        }

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(sidebar));
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

        for (PlayerScoreEntry entry : entries) {
            if (entry.isHidden()) {
                continue;
            }

            String profileId = extractProfile(entry.ownerName());
            if (profileId == null && entry.display() != null) {
                profileId = extractProfile(entry.display());
            }
            if (profileId != null) {
                return profileId;
            }
        }

        return null;
    }

    private static String extractProfile(Component text) {
        if (text == null) {
            return null;
        }

        String raw = text.getString();
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Matcher matcher = PROFILE_PATTERN.matcher(WHITESPACE_PATTERN.matcher(raw).replaceAll(" ").trim());
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
