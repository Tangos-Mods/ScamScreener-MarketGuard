package eu.tango.scamscreener.marketguard.util;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoinFormatTest {
    @AfterEach
    void resetNumberFormat() {
        MarketGuardConfig.setShortNumberFormat(false);
    }

    @Test
    void formatsFullCoinValuesByDefault() {
        assertEquals("1,000", CoinFormat.format(1_000.0D));
        assertEquals("1,000,000", CoinFormat.format(1_000_000.0D));
    }

    @Test
    void formatsShortCoinValuesWhenEnabled() {
        MarketGuardConfig.setShortNumberFormat(true);

        assertEquals("1k", CoinFormat.format(1_000.0D));
        assertEquals("1k", CoinFormat.format(999.6D));
        assertEquals("1.5k", CoinFormat.format(1_500.0D));
        assertEquals("1M", CoinFormat.format(1_000_000.0D));
        assertEquals("1B", CoinFormat.format(1_000_000_000.0D));
    }
}
