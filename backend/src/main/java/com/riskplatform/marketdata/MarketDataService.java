package com.riskplatform.marketdata;

import com.riskplatform.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataService {

    static final String EXPECTED_HEADER = "instrument_id,price_date,price,currency";

    // Postgres upsert on the (instrument_id, price_date) unique key: atomic,
    // idempotent, and safe if two uploads overlap. The previous version did
    // SELECT-then-save per row, which was ~2 round trips per row (no JDBC
    // batching with IDENTITY ids) and raced on concurrent uploads.
    private static final String UPSERT_SQL = """
            INSERT INTO market_price (instrument_id, price_date, price, currency)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (instrument_id, price_date)
            DO UPDATE SET price = EXCLUDED.price, currency = EXCLUDED.currency
            """;

    private static final int BATCH_SIZE = 500;

    private final MarketPriceRepository marketPriceRepository;
    private final JdbcTemplate jdbcTemplate;

    public MarketDataService(MarketPriceRepository marketPriceRepository, JdbcTemplate jdbcTemplate) {
        this.marketPriceRepository = marketPriceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Expects a CSV with header: instrument_id,price_date,price,currency
     * Upserts by (instrument_id, price_date) so re-uploading the same file is safe (idempotent).
     * The whole file is validated before anything is written, so a bad row writes nothing.
     */
    @Transactional
    public int ingestCsv(MultipartFile file) {
        List<PriceRow> rows;
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            rows = parseCsv(reader);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }

        jdbcTemplate.batchUpdate(UPSERT_SQL, rows, BATCH_SIZE, (ps, row) -> {
            ps.setString(1, row.instrumentId());
            ps.setDate(2, Date.valueOf(row.priceDate()));
            ps.setBigDecimal(3, row.price());
            ps.setString(4, row.currency());
        });
        return rows.size();
    }

    static List<PriceRow> parseCsv(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        String header = reader.readLine();
        if (header == null) {
            return List.of();
        }
        if (!header.strip().replace("﻿", "").equalsIgnoreCase(EXPECTED_HEADER)) {
            throw ApiException.badRequest("Expected CSV header '" + EXPECTED_HEADER + "'");
        }

        List<PriceRow> rows = new ArrayList<>();
        String line;
        int lineNumber = 1;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) continue;
            rows.add(parseRow(line, lineNumber));
        }
        return rows;
    }

    private static PriceRow parseRow(String line, int lineNumber) {
        String[] parts = line.split(",", -1);
        if (parts.length != 4) {
            throw ApiException.badRequest("Line " + lineNumber + ": expected 4 columns, found " + parts.length);
        }
        String instrumentId = parts[0].trim();
        String currency = parts[3].trim();
        if (instrumentId.isEmpty() || instrumentId.length() > 50) {
            throw ApiException.badRequest("Line " + lineNumber + ": instrument_id must be 1-50 characters");
        }
        if (!currency.matches("[A-Z]{3}")) {
            throw ApiException.badRequest("Line " + lineNumber + ": currency must be a 3-letter code");
        }
        try {
            LocalDate priceDate = LocalDate.parse(parts[1].trim());
            BigDecimal price = new BigDecimal(parts[2].trim());
            if (price.signum() <= 0) {
                throw ApiException.badRequest("Line " + lineNumber + ": price must be positive");
            }
            return new PriceRow(instrumentId, priceDate, price, currency);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Line " + lineNumber + ": price_date must be YYYY-MM-DD");
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Line " + lineNumber + ": price is not a number");
        }
    }

    @Transactional(readOnly = true)
    public List<MarketPrice> getPriceHistory(String instrumentId) {
        return marketPriceRepository.findByInstrumentIdOrderByPriceDateAsc(instrumentId);
    }

    @Transactional(readOnly = true)
    public List<MarketPrice> getPriceHistory(String instrumentId, LocalDate from, LocalDate to) {
        return marketPriceRepository
                .findByInstrumentIdAndPriceDateBetweenOrderByPriceDateAsc(instrumentId, from, to);
    }

    record PriceRow(String instrumentId, LocalDate priceDate, BigDecimal price, String currency) {}
}
