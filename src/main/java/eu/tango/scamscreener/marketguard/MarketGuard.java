package eu.tango.scamscreener.marketguard;

import eu.tango.scamscreener.marketguard.auction.AuctionOverbidding;
import eu.tango.scamscreener.marketguard.auction.AuctionUnderbidding;
import eu.tango.scamscreener.marketguard.command.MarketGuardCommand;
import eu.tango.scamscreener.marketguard.compat.ScamScreenerBlacklistCompat;
import eu.tango.scamscreener.marketguard.events.AuctionInteractEvent;
import eu.tango.scamscreener.marketguard.hud.AuctionPriceHud;
import eu.tango.scamscreener.marketguard.hud.ForgeProfitHud;
import eu.tango.scamscreener.marketguard.hud.MinionProfitHud;
import eu.tango.scamscreener.marketguard.hud.PlayerHud;
import eu.tango.scamscreener.marketguard.hud.ProfitTrackerHud;
import eu.tango.scamscreener.marketguard.hud.TradeGuardHud;
import eu.tango.scamscreener.marketguard.playerhud.EncounterTracker;
import eu.tango.scamscreener.marketguard.profittracker.ProfitTracker;
import eu.tango.scamscreener.marketguard.screen.HypixelScreens;
import eu.tango.scamscreener.marketguard.update.UpdateJoinNotifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
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
        EncounterTracker.initialize();
        PlayerHud.initialize();
        HypixelScreens.initialize();
        MarketGuardCommand.register();
        ProfitTracker.initialize();
        ProfitTrackerHud.initialize();
        AuctionPriceHud.initialize();
        TradeGuardHud.initialize();
        MinionProfitHud.initialize();
        ForgeProfitHud.initialize();
        UpdateJoinNotifier.initialize();
        registerListeners();
    }

    public static void debug(String message, Object... args) {
        if (!MarketGuardConfig.isDebugEnabled()) return;
        LOGGER.info("[MarketGuard][Debug] " + message, args);
    }

    public static String currentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0.0");
    }

    public static String userAgent() {
        return "MarketGuard/" + currentVersion();
    }

    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private void registerListeners() {
        AuctionInteractEvent.EVENT.register(AuctionUnderbidding::onInteract);
        AuctionInteractEvent.EVENT.register(AuctionOverbidding::onInteract);
        AuctionInteractEvent.EVENT.register(ProfitTracker::onAuctionInteract);
    }
}
