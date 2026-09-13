package eu.tango.scamscreener.marketguard.screen;

import eu.tango.scamscreener.marketguard.MarketGuard;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HypixelScreens {
    private static final Pattern PLAYER_PROFILE = Pattern.compile("^([A-Za-z0-9_]{3,16})'s? Profile$");
    private static final Pattern TRADE_TITLE = Pattern.compile("^You\\s+([A-Za-z0-9_]{3,16})$");
    // Hypixel prefixes the seller with the rank tag, e.g. "Seller: [MVP+] Pankraz01".
    private static final Pattern AUCTION_SELLER = Pattern.compile("^Seller:\\s*(?:\\[[A-Z+]+\\]\\s*)?([A-Za-z0-9_]{3,16})$");
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
        return title != null && TRADE_TITLE.matcher(title.trim()).matches();
    }

    // Hypixel cuts long partner names off in the title; complete them from the trade request or the tab list.
    public static String tradePartner(String title) {
        if (title == null) {
            return null;
        }
        Matcher matcher = TRADE_TITLE.matcher(title.trim());
        if (!matcher.matches()) {
            if (title.startsWith("You")) {
                MarketGuard.debug("Trade title not recognised: '{}'", title);
            }
            return null;
        }

        String displayedName = matcher.group(1);
        String fullName = recentTradePartner;
        if (fullName != null
                && System.currentTimeMillis() - recentTradePartnerAt <= TRADE_REQUEST_TTL_MILLIS
                && fullName.regionMatches(true, 0, displayedName, 0, displayedName.length())) {
            return fullName;
        }
        String onlineName = onlinePlayerStartingWith(displayedName);
        return onlineName != null ? onlineName : displayedName;
    }

    private static String onlinePlayerStartingWith(String prefix) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return null;
        }
        String match = null;
        for (PlayerInfo info : client.getConnection().getListedOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || !name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            if (match != null && !match.equalsIgnoreCase(name)) {
                return null;
            }
            match = name;
        }
        return match;
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
        Matcher matcher = PLAYER_PROFILE.matcher(title.trim());
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
            String text = line.getString().trim();
            Matcher matcher = AUCTION_SELLER.matcher(text);
            if (matcher.matches()) {
                return matcher.group(1);
            }
            if (text.startsWith("Seller:")) {
                MarketGuard.debug("Seller line not recognised: '{}'", text);
            }
        }
        return null;
    }
}
