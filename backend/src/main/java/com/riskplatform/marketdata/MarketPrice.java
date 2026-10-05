package com.riskplatform.marketdata;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "market_price", uniqueConstraints =
    @UniqueConstraint(columnNames = {"instrument_id", "price_date"}))
@Getter
@Setter
@NoArgsConstructor
public class MarketPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false, length = 50)
    private String instrumentId;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;
}