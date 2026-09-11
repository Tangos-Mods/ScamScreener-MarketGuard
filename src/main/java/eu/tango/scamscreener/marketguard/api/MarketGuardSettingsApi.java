package eu.tango.scamscreener.marketguard.api;

/**
 * Stable access to the MarketGuard settings that companion mods may change, for example a modpack setup wizard.
 * Setters persist the change to {@code config/scamscreener_marketguard/config.json}.
 */
public interface MarketGuardSettingsApi {
    /**
     * Indicates whether MarketGuard announces newer Modrinth releases in chat when joining a server.
     */
    boolean updateNotificationsEnabled();

    void setUpdateNotificationsEnabled(boolean enabled);
}
