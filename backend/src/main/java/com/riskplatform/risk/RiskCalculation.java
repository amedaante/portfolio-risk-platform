package com.riskplatform.risk;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_calculation")
@Getter
@Setter
@NoArgsConstructor
public class RiskCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(nullable = false, length = 50)
    private String methodology;

    @Column(name = "confidence_level", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceLevel;

    @Column(name = "horizon_days", nullable = false)
    private Integer horizonDays;

    @Column(name = "calculation_timestamp", nullable = false)
    private LocalDateTime calculationTimestamp = LocalDateTime.now();

    @Column(name = "market_data_as_of", nullable = false)
    private LocalDate marketDataAsOf;

    @Column(name = "portfolio_value", nullable = false, precision = 24, scale = 6)
    private BigDecimal portfolioValue;

    @Column(name = "var_amount", nullable = false, precision = 24, scale = 6)
    private BigDecimal varAmount;

    @Column(name = "expected_shortfall", nullable = false, precision = 24, scale = 6)
    private BigDecimal expectedShortfall;

    @Column(name = "observation_count", nullable = false)
    private Integer observationCount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;
}