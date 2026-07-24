package eu.tango.scamscreener.marketguard.profittracker;

import java.util.regex.Pattern;

enum BazaarSlots {
    ITEM_SLOT(13),
    CLAIM_ALL_COINS_SLOT(32),
    INSTANT_BUY_CONFIRM(Pattern.compile("(?i)(buy now|instant buy|buy instantly|confirm instant buy)")),
    INSTANT_SELL_CONFIRM(Pattern.compile("(?i)(sell now|instant sell|sell instantly|confirm instant sell)")),
    BUY_ORDER_CONFIRM(Pattern.compile("(?i)(create buy order|confirm buy order|place buy order)")),
    SELL_ORDER_CONFIRM(Pattern.compile("(?i)(create sell offer|create sell order|confirm sell offer|confirm sell order|place sell offer)"));

    private final int slot;
    private final Pattern buttonPattern;

    BazaarSlots(int slot) {
        this.slot = slot;
        this.buttonPattern = null;
    }

    BazaarSlots(Pattern buttonPattern) {
        this.slot = -1;
        this.buttonPattern = buttonPattern;
    }

    int slot() {
        return slot;
    }

    boolean matches(String value) {
        return value != null && buttonPattern != null && buttonPattern.matcher(value).find();
    }

    static BazaarTradeKind resolveKind(String buttonName) {
        if (INSTANT_BUY_CONFIRM.matches(buttonName)) {
            return BazaarTradeKind.INSTANT_BUY;
        }
        if (INSTANT_SELL_CONFIRM.matches(buttonName)) {
            return BazaarTradeKind.INSTANT_SELL;
        }
        if (BUY_ORDER_CONFIRM.matches(buttonName)) {
            return BazaarTradeKind.BUY_ORDER;
        }
        if (SELL_ORDER_CONFIRM.matches(buttonName)) {
            return BazaarTradeKind.SELL_ORDER;
        }
        return null;
    }

    static boolean isClaimAllCoins(int slotId, String buttonName) {
        return slotId == CLAIM_ALL_COINS_SLOT.slot && buttonName != null
                && buttonName.toLowerCase(java.util.Locale.ROOT).contains("claim all coins");
    }
}
