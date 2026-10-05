package com.riskplatform.risk.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class RiskCalculationDtos {

    public record HistoricalVarCalculationResponse(
            Long id,
            Long portfolioId,
            String methodology,
            BigDecimal confidenceLevel,
            Integer horizonDays,
            LocalDateTime calculationTimestamp,
            LocalDate marketDataAsOf,
            BigDecimal portfolioValue,
            BigDecimal varAmount,
            BigDecimal expectedShortfall,
            Integer observationCount,
            String status
    ) {}
}