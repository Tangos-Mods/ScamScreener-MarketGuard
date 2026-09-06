package eu.tango.scamscreener.marketguard.auction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record AuctionReferencePrice(double value, Quality quality, int signalCount, double relativeSpread) {
    private static final double HIGH_QUALITY_MAX_SPREAD = 0.20;
    private static final double MEDIUM_QUALITY_MAX_SPREAD = 0.35;

    public enum Quality {
        HIGH,
        MEDIUM,
        LOW
    }

    public static Optional<AuctionReferencePrice> select(Double lowestBin, Double average7d, Double average30d) {
        List<Double> prices = new ArrayList<>(3);
        addValid(prices, lowestBin);
        addValid(prices, average7d);
        addValid(prices, average30d);
        if (prices.isEmpty()) {
            return Optional.empty();
        }

        prices.sort(Double::compareTo);
        double value = prices.size() % 2 == 0
                ? (prices.get(prices.size() / 2 - 1) + prices.get(prices.size() / 2)) / 2.0
                : prices.get(prices.size() / 2);
        double relativeSpread = (prices.getLast() - prices.getFirst()) / value;
        Quality quality;
        if (prices.size() == 3 && relativeSpread <= HIGH_QUALITY_MAX_SPREAD) {
            quality = Quality.HIGH;
        } else if (prices.size() >= 2 && relativeSpread <= MEDIUM_QUALITY_MAX_SPREAD) {
            quality = Quality.MEDIUM;
        } else {
            quality = Quality.LOW;
        }

        return Optional.of(new AuctionReferencePrice(value, quality, prices.size(), relativeSpread));
    }

    public boolean safeForProtection() {
        return quality != Quality.LOW;
    }

    private static void addValid(List<Double> prices, Double price) {
        if (price != null && Double.isFinite(price) && price > 0.0) {
            prices.add(price);
        }
    }
}
