package eu.tango.scamscreener.marketguard;

import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.command.MarketGuardCommand;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.update.UpdateJoinNotifier;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketGuard implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("marketguard");
    public static final String MOD_ID = "marketguard";

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} initialized on client", MOD_ID);
        MarketGuardConfig.load();
        ScamScreenerBlacklistCompat.initialize();
        MarketGuardCommand.register();
        UpdateJoinNotifier.initialize();
        registerListeners();
    }

    public static void debug(String message, Object... args) {
        if (!MarketGuardConfig.isDebugEnabled()) return;
        LOGGER.info("[MarketGuard][Debug] " + message, args);
    }

    public static Identifier id(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    private void registerListeners() {
        AuctionInteractEvent.EVENT.register(AuctionUnderbidding::onInteract);
        AuctionInteractEvent.EVENT.register(AuctionOverbidding::onInteract);
    }
}
