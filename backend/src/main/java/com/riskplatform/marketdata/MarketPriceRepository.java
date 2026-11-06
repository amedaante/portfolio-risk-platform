package com.riskplatform.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    List<MarketPrice> findByInstrumentIdOrderByPriceDateAsc(String instrumentId);

    // One query for every instrument in a portfolio, instead of one per position.
    List<MarketPrice> findByInstrumentIdInOrderByInstrumentIdAscPriceDateAsc(Collection<String> instrumentIds);

    Optional<MarketPrice> findByInstrumentIdAndPriceDate(String instrumentId, LocalDate priceDate);

    List<MarketPrice> findByInstrumentIdAndPriceDateBetweenOrderByPriceDateAsc(
            String instrumentId, LocalDate from, LocalDate to);
}
