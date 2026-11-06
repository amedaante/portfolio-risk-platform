package com.riskplatform.marketdata;

import com.riskplatform.common.ApiException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Converts amounts into one base currency using FX pair prices from
 * market_price. A pair "XXXYYY" priced at S means 1 XXX = S YYY, so:
 *   rate(XXX -> YYY) = S       (pair XXXYYY exists)
 *   rate(YYY -> XXX) = 1 / S   (only the inverse pair exists)
 *
 * Not a Spring bean: create one per request/calculation through
 * FxRateService so rate histories are loaded at most once per currency.
 */
public class FxConverter {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private final String baseCurrency;
    private final Function<String, List<MarketPrice>> pairHistoryLoader;
    private final Map<String, NavigableMap<LocalDate, BigDecimal>> cache = new HashMap<>();

    FxConverter(String baseCurrency, Function<String, List<MarketPrice>> pairHistoryLoader) {
        this.baseCurrency = baseCurrency;
        this.pairHistoryLoader = pairHistoryLoader;
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    public boolean isBase(String currency) {
        return baseCurrency.equals(currency);
    }

    /** Units of base currency per 1 unit of {@code currency}, by date. */
    public NavigableMap<LocalDate, BigDecimal> rateHistory(String currency) {
        return cache.computeIfAbsent(currency, this::loadRateHistory);
    }

    /** Rate on {@code date}, or the most recent rate before it. */
    public BigDecimal rateOn(String currency, LocalDate date) {
        if (isBase(currency)) {
            return BigDecimal.ONE;
        }
        Map.Entry<LocalDate, BigDecimal> entry = rateHistory(currency).floorEntry(date);
        if (entry == null) {
            throw ApiException.unprocessable(
                    "No " + currency + "/" + baseCurrency + " FX rate on or before " + date);
        }
        return entry.getValue();
    }

    public BigDecimal latestRate(String currency) {
        if (isBase(currency)) {
            return BigDecimal.ONE;
        }
        return rateHistory(currency).lastEntry().getValue();
    }

    public BigDecimal toBase(BigDecimal amount, String currency, LocalDate date) {
        return amount.multiply(rateOn(currency, date));
    }

    private NavigableMap<LocalDate, BigDecimal> loadRateHistory(String currency) {
        NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();
        if (isBase(currency)) {
            return rates;
        }

        List<MarketPrice> direct = pairHistoryLoader.apply(currency + baseCurrency);
        if (!direct.isEmpty()) {
            direct.forEach(p -> rates.put(p.getPriceDate(), p.getPrice()));
            return rates;
        }

        List<MarketPrice> inverse = pairHistoryLoader.apply(baseCurrency + currency);
        if (!inverse.isEmpty()) {
            inverse.forEach(p -> rates.put(p.getPriceDate(), BigDecimal.ONE.divide(p.getPrice(), PRECISION)));
            return rates;
        }

        throw ApiException.unprocessable("No FX rate to convert " + currency + " to " + baseCurrency
                + "; upload prices for " + currency + baseCurrency + " or " + baseCurrency + currency);
    }
}
