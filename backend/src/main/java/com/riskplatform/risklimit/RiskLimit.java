package com.riskplatform.risklimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_limit")
@Getter
@Setter
@NoArgsConstructor
public class RiskLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 30)
    private LimitType limitType;

    @Column(name = "limit_value", nullable = false, precision = 24, scale = 6)
    private BigDecimal limitValue;

    @Column(name = "warning_threshold_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal warningThresholdPct = BigDecimal.valueOf(80.00);

    // Optional scope for VAR / EXPECTED_SHORTFALL limits; null means "any".
    @Column(length = 50)
    private String methodology;

    @Column(name = "confidence_level", precision = 5, scale = 4)
    private BigDecimal confidenceLevel;

    @Column(name = "horizon_days")
    private Integer horizonDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum LimitType {
        VAR, EXPECTED_SHORTFALL, EQUITY_EXPOSURE_PCT, FX_EXPOSURE
    }
}