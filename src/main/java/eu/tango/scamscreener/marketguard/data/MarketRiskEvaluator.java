package eu.tango.scamscreener.marketguard.data;

public final class MarketRiskEvaluator {
    private static final double AUCTION_AVERAGE_SHIFT_WARNING = 0.15;
    private static final double AUCTION_AVERAGE_SHIFT_HIGH = 0.30;
    private static final double BAZAAR_SPREAD_WARNING = 0.05;
    private static final double BAZAAR_SPREAD_HIGH = 0.15;
    private static final long BAZAAR_BOOK_SIDE_WARNING = 1_000L;
    private static final long BAZAAR_BOOK_SIDE_HIGH = 100L;
    private static final long BAZAAR_WEEKLY_TURNOVER_WARNING = 5_000L;
    private static final long BAZAAR_WEEKLY_TURNOVER_HIGH = 500L;

    private MarketRiskEvaluator() {}

    public static Warning auctionVolatility(Double average7d, Double average30d) {
        if (!positive(average7d) || !positive(average30d)) {
            return null;
        }

        double change = (average7d - average30d) / average30d;
        if (Math.abs(change) < AUCTION_AVERAGE_SHIFT_WARNING) {
            return null;
        }

        String direction = change > 0.0 ? "rising" : "falling";
        return new Warning(
                "Price trend: " + direction + " " + Math.round(Math.abs(change) * 100.0) + "% vs. last month",
                Math.abs(change) >= AUCTION_AVERAGE_SHIFT_HIGH
        );
    }

    public static Warning bazaarLiquidity(BazaarData.Product product) {
        if (product == null) {
            return null;
        }

        boolean warning = false;
        boolean highRisk = false;

        if (Double.isFinite(product.buy()) && Double.isFinite(product.sell()) && product.buy() > 0.0 && product.sell() >= 0.0) {
            double spread = Math.max(0.0, product.buy() - product.sell()) / product.buy();
            if (spread >= BAZAAR_SPREAD_WARNING) {
                warning = true;
                highRisk = spread >= BAZAAR_SPREAD_HIGH;
            }
        }

        if (nonNegative(product.buyVolume()) && nonNegative(product.sellVolume())) {
            long thinnerSide = Math.min(product.buyVolume(), product.sellVolume());
            if (thinnerSide < BAZAAR_BOOK_SIDE_WARNING) {
                warning = true;
                highRisk |= thinnerSide < BAZAAR_BOOK_SIDE_HIGH;
            }
        }

        if (nonNegative(product.buyMovingWeek()) && nonNegative(product.sellMovingWeek())) {
            long weeklyTurnover = Math.min(product.buyMovingWeek(), product.sellMovingWeek());
            if (weeklyTurnover < BAZAAR_WEEKLY_TURNOVER_WARNING) {
                warning = true;
                highRisk |= weeklyTurnover < BAZAAR_WEEKLY_TURNOVER_HIGH;
            }
        }

        if (!warning) {
            return null;
        }

        return new Warning("Hard to resell on the Bazaar", highRisk);
    }

    private static boolean positive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }

    private static boolean nonNegative(Long value) {
        return value != null && value >= 0L;
    }

    public record Warning(String text, boolean highRisk) {}
}
