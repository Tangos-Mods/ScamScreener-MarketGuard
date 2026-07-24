package eu.tango.scamscreener.marketguard.util;

import eu.tango.scamscreener.marketguard.MarketGuardConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoinFormat {
    public static String format(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (!MarketGuardConfig.isShortNumberFormat()) {
            return String.format(Locale.US, "%,.0f", value);
        }

        double absoluteValue = Math.abs(value);
        if (absoluteValue < 1_000.0D) {
            if (absoluteValue >= 999.5D) {
                return value < 0.0D ? "-1k" : "1k";
            }
            return String.format(Locale.US, "%.0f", value);
        }

        String sign = value < 0.0D ? "-" : "";
        if (absoluteValue >= 999_950_000.0D) {
            return sign + compact(absoluteValue / 1_000_000_000.0D) + "B";
        }
        if (absoluteValue >= 999_950.0D) {
            return sign + compact(absoluteValue / 1_000_000.0D) + "M";
        }
        return sign + compact(absoluteValue / 1_000.0D) + "k";
    }

    public static String coins(double value) {
        return format(value) + " coins";
    }

    public static String formatWithDecimals(double value) {
        if (MarketGuardConfig.isShortNumberFormat()) {
            return format(value);
        }
        if (Math.abs(value - Math.rint(value)) < 0.005D) {
            return String.format(Locale.US, "%,.0f", value);
        }
        return String.format(Locale.US, "%,.2f", value);
    }

    private static String compact(double value) {
        String formatted = String.format(Locale.US, "%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }
}
