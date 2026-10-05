package com.riskplatform.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PortfolioDtos {

    public record CreatePortfolioRequest(
            @NotBlank String name,
            @NotBlank @Size(min = 3, max = 3) String baseCurrency
    ) {}

    public record PortfolioResponse(
            Long id,
            String name,
            String baseCurrency,
            BigDecimal totalMarketValue
    ) {}

    public record AddPositionRequest(
            @NotBlank String instrumentId,
            @NotBlank String instrumentType,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal price,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull LocalDate valuationDate
    ) {}

    public record PositionResponse(
            Long id,
            String instrumentId,
            String instrumentType,
            BigDecimal quantity,
            BigDecimal price,
            String currency,
            LocalDate valuationDate,
            BigDecimal marketValue
    ) {}
}