package eu.tango.scamscreener.marketguard.compat;

import eu.tango.scamscreener.marketguard.MarketGuard;
import net.fabricmc.loader.api.FabricLoader;

import java.util.UUID;
import java.util.function.Function;

public final class ScamScreenerBlacklistCompat {
    private static final Object LOCK = new Object();
    private static final Function<UUID, String> NO_BLACKLIST = uuid -> null;

    private static volatile boolean initialized = false;
    private static volatile Function<UUID, String> blacklistedNameLookup = NO_BLACKLIST;

    private ScamScreenerBlacklistCompat() {}

    public static void initialize() {
        ensureInitialized();
    }

    public static String findBlacklistedPlayerName(String rawUuid) {
        UUID uuid = parseUuid(rawUuid);
        if (uuid == null) {
            MarketGuard.debug("ScamScreener blacklist lookup skipped rawUuid='{}' parsedUuid=<invalid>", rawUuid);
            return null;
        }

        ensureInitialized();
        try {
            String playerName = blacklistedNameLookup.apply(uuid);
            if (playerName != null) {
                MarketGuard.debug("ScamScreener blacklist lookup matched uuid='{}' player='{}'", uuid, playerName);
            } else {
                MarketGuard.debug("ScamScreener blacklist lookup found no match for uuid='{}'", uuid);
            }
            return playerName;
        } catch (RuntimeException e) {
            MarketGuard.LOGGER.warn("ScamScreener blacklist lookup failed: {}", e.getMessage(), e);
            disable();
            return null;
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (LOCK) {
            if (initialized) {
                return;
            }

            initialized = true;

            try {
                if (!FabricLoader.getInstance().isModLoaded("scamscreener")) {
                    MarketGuard.debug("ScamScreener mod is not loaded");
                    return;
                }

                blacklistedNameLookup = ApiBridge.createBlacklistLookup();
                MarketGuard.debug("ScamScreener blacklist integration initialized");
            } catch (RuntimeException | LinkageError e) {
                MarketGuard.LOGGER.warn("ScamScreener integration could not be initialized: {}", e.getMessage(), e);
                disable();
            }
        }
    }

    private static void disable() {
        synchronized (LOCK) {
            blacklistedNameLookup = NO_BLACKLIST;
            initialized = true;
        }
    }

    private static UUID parseUuid(String rawUuid) {
        if (rawUuid == null) {
            return null;
        }

        String normalized = rawUuid.trim().replace("-", "");
        if (normalized.length() != 32) {
            return null;
        }

        String hyphenated = normalized.substring(0, 8)
                + "-" + normalized.substring(8, 12)
                + "-" + normalized.substring(12, 16)
                + "-" + normalized.substring(16, 20)
                + "-" + normalized.substring(20);

        try {
            return UUID.fromString(hyphenated);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static final class ApiBridge {
        private ApiBridge() {}

        private static Function<UUID, String> createBlacklistLookup() {
            var apis = FabricLoader.getInstance().getEntrypoints(
                    eu.tango.scamscreener.api.ScamScreenerApi.ENTRYPOINT_KEY,
                    eu.tango.scamscreener.api.ScamScreenerApi.class
            );
            if (apis.isEmpty()) {
                MarketGuard.debug(
                        "ScamScreener API entrypoint '{}' not found",
                        eu.tango.scamscreener.api.ScamScreenerApi.ENTRYPOINT_KEY
                );
                return NO_BLACKLIST;
            }

            var blacklist = apis.get(0).blacklist();
            return uuid -> blacklist.get(uuid)
                    .map(entry -> {
                        String playerName = entry.playerName();
                        return playerName == null || playerName.isBlank() ? uuid.toString() : playerName;
                    })
                    .orElse(null);
        }
    }
}
