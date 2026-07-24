package eu.tango.scamscreener.marketguard.screen;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HypixelScreens {
    private static final Pattern PLAYER_PROFILE = Pattern.compile("^([A-Za-z0-9_]{3,16})'s Profile$");
    private static final Pattern TRADE_TITLE = Pattern.compile("^You\\s+([A-Za-z0-9_]{3,16})$");
    private static final Pattern AUCTION_SELLER = Pattern.compile("^Seller:\\s*([A-Za-z0-9_]{3,16})$");
    private static final Pattern OUTGOING_TRADE_REQUEST = Pattern.compile("^You have sent a trade request to ([A-Za-z0-9_]{3,16})\\.$");
    private static final Pattern INCOMING_TRADE_REQUEST = Pattern.compile("^([A-Za-z0-9_]{3,16}) has sent you a trade request!$");
    private static final long TRADE_REQUEST_TTL_MILLIS = 30_000L;
    private static volatile String recentTradePartner;
    private static volatile long recentTradePartnerAt;

    private HypixelScreens() {}

    public static void initialize() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> rememberTradePartner(message.getString()));
    }

    public static boolean isTrade(String title) {
        return tradePartner(title) != null;
    }

    public static String tradePartner(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = TRADE_TITLE.matcher(title);
        if (!matcher.matches()) {
            return null;
        }

        String displayedName = matcher.group(1);
        String fullName = recentTradePartner;
        if (fullName != null
                && System.currentTimeMillis() - recentTradePartnerAt <= TRADE_REQUEST_TTL_MILLIS
                && fullName.regionMatches(true, 0, displayedName, 0, displayedName.length())) {
            return fullName;
        }
        return displayedName;
    }

    static void rememberTradePartner(String message) {
        Matcher outgoing = OUTGOING_TRADE_REQUEST.matcher(message);
        Matcher incoming = INCOMING_TRADE_REQUEST.matcher(message);
        if (outgoing.matches()) {
            recentTradePartner = outgoing.group(1);
            recentTradePartnerAt = System.currentTimeMillis();
        } else if (incoming.matches()) {
            recentTradePartner = incoming.group(1);
            recentTradePartnerAt = System.currentTimeMillis();
        }
    }

    public static String profilePlayer(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = PLAYER_PROFILE.matcher(title);
        return matcher.matches() ? matcher.group(1) : null;
    }

    public static String binSeller(AbstractContainerMenu menu) {
        if (menu == null || menu.slots.size() <= 13) {
            return null;
        }

        ItemStack stack = menu.getSlot(13).getItem();
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        ItemLore lore = stack.get(DataComponents.LORE);
        return lore == null ? null : auctionSeller(lore.lines());
    }

    static String auctionSeller(Iterable<net.minecraft.network.chat.Component> loreLines) {
        for (net.minecraft.network.chat.Component line : loreLines) {
            Matcher matcher = AUCTION_SELLER.matcher(line.getString().trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
