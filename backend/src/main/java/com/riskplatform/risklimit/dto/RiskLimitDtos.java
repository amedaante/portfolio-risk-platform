package com.riskplatform.risklimit.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class RiskLimitDtos {

    public record CreateRiskLimitRequest(
            @NotNull String limitType,
            @NotNull BigDecimal limitValue,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal warningThresholdPct
    ) {}

    public record RiskLimitResponse(
            Long id,
            Long portfolioId,
            String limitType,
            BigDecimal limitValue,
            BigDecimal warningThresholdPct
    ) {}

    public record LimitEvaluation(
            Long limitId,
            String limitType,
            BigDecimal limitValue,
            BigDecimal currentValue,
            BigDecimal utilizationPct, // currentValue / limitValue * 100
            String status              // WITHIN, WARNING, BREACHED
    ) {}

    public record PortfolioLimitEvaluationResponse(
            Long portfolioId,
            List<LimitEvaluation> evaluations
    ) {}
}