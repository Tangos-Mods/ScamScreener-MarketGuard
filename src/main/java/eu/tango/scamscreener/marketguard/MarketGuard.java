package eu.tango.scamscreener.marketguard;

import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarketGuard implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("marketguard");
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "1.21.11";

    public static final String MOD_ID = "marketguard";

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} initialized on client", MOD_ID);
        registerListeners();
    }

    public static Identifier id(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    private void registerListeners() {
        AuctionInteractEvent.EVENT.register(AuctionUnderbidding::onInteract);
        AuctionInteractEvent.EVENT.register(AuctionOverbidding::onInteract);
    }
}
