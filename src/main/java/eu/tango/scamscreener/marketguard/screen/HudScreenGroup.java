package eu.tango.scamscreener.marketguard.screen;

import eu.tango.scamscreener.marketguard.auction.AuctionInventory;
import eu.tango.scamscreener.marketguard.hud.ForgeProfitHud;
import eu.tango.scamscreener.marketguard.hud.MinionProfitHud;

import java.util.EnumSet;
import java.util.Set;

public enum HudScreenGroup {
    INGAME,
    AUCTION_HOUSE,
    BIN_VIEW,
    TRADE,
    PROFILE,
    MINION,
    FORGE;

    public static Set<HudScreenGroup> classify(String title) {
        if (title == null || title.isBlank()) {
            return EnumSet.of(INGAME);
        }

        EnumSet<HudScreenGroup> groups = EnumSet.noneOf(HudScreenGroup.class);
        if (AuctionInventory.matchesAny(title)) {
            groups.add(AUCTION_HOUSE);
        }
        if (AuctionInventory.BIN_VIEW.matches(title)) {
            groups.add(BIN_VIEW);
        }
        if (HypixelScreens.isTrade(title)) {
            groups.add(TRADE);
        }
        if (HypixelScreens.profilePlayer(title) != null) {
            groups.add(PROFILE);
        }
        if (MinionProfitHud.isMinionScreen(title)) {
            groups.add(MINION);
        }
        if (ForgeProfitHud.isForgeScreen(title)) {
            groups.add(FORGE);
        }
        return groups;
    }
}
