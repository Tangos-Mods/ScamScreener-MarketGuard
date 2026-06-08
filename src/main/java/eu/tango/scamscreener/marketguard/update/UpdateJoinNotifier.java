package eu.tango.scamscreener.marketguard.update;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import static eu.tango.scamscreener.marketguard.util.MessageBuilder.updateAvailable;

public final class UpdateJoinNotifier {
    private static boolean initialized;
    private static String lastNotifiedVersion = "";

    private UpdateJoinNotifier() {}

    public static void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ModrinthUpdateChecker.checkOnJoin(updateInfo -> client.execute(() -> notifyOnce(client, updateInfo)))
        );
    }

    private static synchronized void notifyOnce(Minecraft client, ModrinthUpdateChecker.UpdateInfo updateInfo) {
        if (client.player == null || updateInfo == null || updateInfo.latestVersion() == null || updateInfo.latestVersion().isBlank()) {
            return;
        }
        if (updateInfo.latestVersion().equalsIgnoreCase(lastNotifiedVersion)) {
            return;
        }

        lastNotifiedVersion = updateInfo.latestVersion();
        client.player.sendSystemMessage(updateAvailable(
                updateInfo.currentVersion(),
                updateInfo.latestVersion(),
                updateInfo.modrinthUrl(),
                updateInfo.changelog()
        ));
    }
}
