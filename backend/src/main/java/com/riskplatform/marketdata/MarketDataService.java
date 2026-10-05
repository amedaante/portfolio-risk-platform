package com.riskplatform.marketdata;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class MarketDataService {

    private final MarketPriceRepository marketPriceRepository;

    public MarketDataService(MarketPriceRepository marketPriceRepository) {
        this.marketPriceRepository = marketPriceRepository;
    }

    /**
     * Expects a CSV with header: instrument_id,price_date,price,currency
     * Upserts by (instrument_id, price_date) so re-uploading the same file is safe (idempotent).
     */
    @Transactional
    public int ingestCsv(MultipartFile file) {
        List<MarketPrice> toSave = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header row
            if (header == null) return 0;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");

                String instrumentId = parts[0].trim();
                LocalDate priceDate = LocalDate.parse(parts[1].trim());
                BigDecimal price = new BigDecimal(parts[2].trim());
                String currency = parts[3].trim();

                MarketPrice mp = marketPriceRepository
                        .findByInstrumentIdAndPriceDate(instrumentId, priceDate)
                        .orElseGet(MarketPrice::new);

                mp.setInstrumentId(instrumentId);
                mp.setPriceDate(priceDate);
                mp.setPrice(price);
                mp.setCurrency(currency);

                toSave.add(mp);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read market data CSV", e);
        }

        marketPriceRepository.saveAll(toSave);
        return toSave.size();
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
}