package com.riskplatform.risk.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class QuantEngineDtos {

    public record PricePoint(LocalDate date, BigDecimal price) {}

    public record PositionInput(
            String instrumentId,
            BigDecimal quantity,
            BigDecimal currentPrice,
            List<PricePoint> priceHistory
    ) {}

    public record HistoricalVarRequest(
            BigDecimal confidenceLevel,
            Integer horizonDays,
            List<PositionInput> positions
    ) {}

    public record HistoricalVarResponse(
            String methodology,
            BigDecimal confidenceLevel,
            Integer horizonDays,
            BigDecimal portfolioValue,
            BigDecimal var,
            BigDecimal expectedShortfall,
            Integer observationCount,
            BigDecimal worstLoss,
            BigDecimal bestGain
    ) {}

    public record ParametricVarResponse(
            String methodology,
            BigDecimal confidenceLevel,
            Integer horizonDays,
            BigDecimal portfolioValue,
            BigDecimal var,
            BigDecimal expectedShortfall,
            Integer observationCount,
            BigDecimal portfolioVolatility1day,
            BigDecimal zScore
    ) {}

    public record MonteCarloVarRequest(
            BigDecimal confidenceLevel,
            Integer horizonDays,
            Integer numSimulations,
            Long randomSeed,
            List<PositionInput> positions
    ) {}

    public record MonteCarloVarResponse(
            String methodology,
            BigDecimal confidenceLevel,
            Integer horizonDays,
            BigDecimal portfolioValue,
            BigDecimal var,
            BigDecimal expectedShortfall,
            Integer observationCount,
            Integer numSimulations,
            Long randomSeed,
            BigDecimal worstLoss,
            BigDecimal bestGain
    ) {}

    public record RiskContributionItem(
            String instrumentId,
            BigDecimal exposure,
            BigDecimal componentVar,
            BigDecimal pctOfTotalVar
    ) {}

    public record RiskContributionResponse(
            BigDecimal confidenceLevel,
            Integer horizonDays,
            BigDecimal portfolioValue,
            BigDecimal totalVar,
            List<RiskContributionItem> contributions
    ) {}

    public record StressPositionInput(
            String instrumentId,
            String instrumentType,
            BigDecimal quantity,
            BigDecimal currentPrice,
            BigDecimal modifiedDuration,  // nullable: BOND only
            BigDecimal convexity          // nullable: BOND only
    ) {}

    public record StressScenarioRequest(
            String scenarioName,
            BigDecimal equityShockPct,
            BigDecimal rateShockBps,
            BigDecimal fxShockPct,
            BigDecimal defaultBondDuration,
            BigDecimal defaultBondConvexity,
            List<StressPositionInput> positions
    ) {}

    public record StressPositionImpact(
            String instrumentId,
            String instrumentType,
            BigDecimal baseValue,
            BigDecimal stressedValue,
            BigDecimal pnl,
            BigDecimal pnlPct
    ) {}

    public record StressScenarioResponse(
            String scenarioName,
            BigDecimal equityShockPct,
            BigDecimal rateShockBps,
            BigDecimal fxShockPct,
            BigDecimal basePortfolioValue,
            BigDecimal stressedPortfolioValue,
            BigDecimal totalPnl,
            BigDecimal totalPnlPct,
            List<StressPositionImpact> positionImpacts
    ) {}
}