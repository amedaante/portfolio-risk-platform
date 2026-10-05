package com.riskplatform.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    List<MarketPrice> findByInstrumentIdOrderByPriceDateAsc(String instrumentId);

    Optional<MarketPrice> findByInstrumentIdAndPriceDate(String instrumentId, LocalDate priceDate);

    List<MarketPrice> findByInstrumentIdAndPriceDateBetweenOrderByPriceDateAsc(
            String instrumentId, LocalDate from, LocalDate to);
}