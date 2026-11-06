package com.riskplatform.marketdata;

import com.riskplatform.common.ApiException;
import com.riskplatform.marketdata.MarketDataService.PriceRow;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketDataCsvParsingTest {

    private static final String HEADER = "instrument_id,price_date,price,currency\n";

    @Test
    void parsesValidRowsAndSkipsBlankLines() throws Exception {
        List<PriceRow> rows = MarketDataService.parseCsv(new StringReader(
                HEADER + "AAPL,2026-09-24,190.5,USD\n\nAAPL,2026-09-25,191,USD\n"));

        assertEquals(2, rows.size());
        assertEquals(new PriceRow("AAPL", LocalDate.of(2026, 9, 24), new BigDecimal("190.5"), "USD"), rows.get(0));
    }

    @Test
    void malformedRowIsA400NamingTheLine() {
        // Previously: ArrayIndexOutOfBoundsException -> HTTP 500
        ApiException ex = assertThrows(ApiException.class, () -> MarketDataService.parseCsv(new StringReader(
                HEADER + "AAPL,2026-09-24,190.5,USD\nAAPL,2026-09-25\n")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().startsWith("Line 3:"), ex.getMessage());
    }

    @Test
    void badDateAndBadPriceAreRejected() {
        assertThrows(ApiException.class, () -> MarketDataService.parseCsv(new StringReader(
                HEADER + "AAPL,25/09/2026,190.5,USD\n")));
        assertThrows(ApiException.class, () -> MarketDataService.parseCsv(new StringReader(
                HEADER + "AAPL,2026-09-25,abc,USD\n")));
        assertThrows(ApiException.class, () -> MarketDataService.parseCsv(new StringReader(
                HEADER + "AAPL,2026-09-25,-1,USD\n")));
    }

    @Test
    void wrongHeaderIsRejected() {
        assertThrows(ApiException.class, () -> MarketDataService.parseCsv(new StringReader(
                "ticker,date,close\nAAPL,2026-09-25,1\n")));
    }
}
