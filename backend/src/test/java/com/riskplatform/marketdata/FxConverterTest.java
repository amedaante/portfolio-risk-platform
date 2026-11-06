package com.riskplatform.marketdata;

import com.riskplatform.common.ApiException;
import com.riskplatform.portfolio.Position;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FxConverterTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 24);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 25);

    // Only USDINR and EURUSD exist, as in the demo data.
    private final FxConverter toUsd = new FxConverter("USD", pair -> Map.of(
            "USDINR", List.of(price("USDINR", D1, "80"), price("USDINR", D2, "73.383241")),
            "EURUSD", List.of(price("EURUSD", D2, "0.866133"))
    ).getOrDefault(pair, List.of()));

    @Test
    void directPairMultiplies() {
        // 1 EUR = 0.866133 USD
        assertEquals(0, new BigDecimal("866.133").compareTo(toUsd.toBase(new BigDecimal("1000"), "EUR", D2)));
    }

    @Test
    void inversePairDivides_usdInrPositionIsWorthItsUsdNotional() {
        // The demo bug: 50,000 USDINR at 73.38 was counted as 3.67M "USD".
        // It's 3.67M INR, which is 50,000 USD.
        BigDecimal valueInInr = new BigDecimal("50000").multiply(new BigDecimal("73.383241"));
        BigDecimal valueInUsd = toUsd.toBase(valueInInr, "INR", D2);
        assertEquals(50000.0, valueInUsd.doubleValue(), 1e-6);
    }

    @Test
    void usesMostRecentRateOnOrBeforeTheDate() {
        assertEquals(0, new BigDecimal("1").divide(new BigDecimal("80"), java.math.MathContext.DECIMAL64)
                .compareTo(toUsd.rateOn("INR", D1.plusDays(0))));
        assertEquals(toUsd.rateOn("INR", D2), toUsd.rateOn("INR", D2.plusDays(10)));
    }

    @Test
    void baseCurrencyConvertsAtOne() {
        assertEquals(BigDecimal.ONE, toUsd.rateOn("USD", D2));
    }

    @Test
    void missingRateIsUnprocessableWithAHelpfulMessage() {
        ApiException ex = assertThrows(ApiException.class, () -> toUsd.rateOn("JPY", D2));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertTrue(ex.getMessage().contains("JPYUSD"));
    }

    @Test
    void noRateBeforeDateIsUnprocessable() {
        assertThrows(ApiException.class, () -> toUsd.rateOn("INR", D1.minusDays(1)));
    }

    @Test
    void fxPairIsPricedInItsQuoteCurrencyWhateverTheStoredColumnSays() {
        Position usdInr = new Position();
        usdInr.setInstrumentId("USDINR");
        usdInr.setInstrumentType(Position.InstrumentType.FX);
        usdInr.setCurrency("USD"); // what the demo data stored
        assertEquals("INR", usdInr.priceCurrency());

        Position aapl = new Position();
        aapl.setInstrumentId("AAPL");
        aapl.setInstrumentType(Position.InstrumentType.EQUITY);
        aapl.setCurrency("USD");
        assertEquals("USD", aapl.priceCurrency());
    }

    private static MarketPrice price(String instrumentId, LocalDate date, String value) {
        MarketPrice price = new MarketPrice();
        price.setInstrumentId(instrumentId);
        price.setPriceDate(date);
        price.setPrice(new BigDecimal(value));
        price.setCurrency("XXX");
        return price;
    }
}
