package com.riskplatform.risklimit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public class RiskLimitDtos {

    public record CreateRiskLimitRequest(
            @NotNull @Pattern(regexp = "VAR|EXPECTED_SHORTFALL|EQUITY_EXPOSURE_PCT|FX_EXPOSURE",
                    message = "must be one of VAR, EXPECTED_SHORTFALL, EQUITY_EXPOSURE_PCT, FX_EXPOSURE")
            String limitType,
            @NotNull @PositiveOrZero BigDecimal limitValue,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal warningThresholdPct,
            // Optional scope for VAR / EXPECTED_SHORTFALL limits
            @Pattern(regexp = "HISTORICAL_SIMULATION|PARAMETRIC|MONTE_CARLO",
                    message = "must be one of HISTORICAL_SIMULATION, PARAMETRIC, MONTE_CARLO")
            String methodology,
            @DecimalMin(value = "0.5", inclusive = false) @DecimalMax(value = "1", inclusive = false)
            BigDecimal confidenceLevel,
            @Min(1) @Max(250) Integer horizonDays
    ) {}

    public record RiskLimitResponse(
            Long id,
            Long portfolioId,
            String limitType,
            BigDecimal limitValue,
            BigDecimal warningThresholdPct,
            String methodology,
            BigDecimal confidenceLevel,
            Integer horizonDays
    ) {}

    public record LimitEvaluation(
            Long limitId,
            String limitType,
            BigDecimal limitValue,
            BigDecimal currentValue,   // null when status is NO_DATA
            BigDecimal utilizationPct, // currentValue / limitValue * 100; null when NO_DATA
            String status,             // WITHIN, WARNING, BREACHED, NO_DATA
            String basis               // what currentValue was measured from
    ) {}

    public record PortfolioLimitEvaluationResponse(
            Long portfolioId,
            List<LimitEvaluation> evaluations
    ) {}
}
