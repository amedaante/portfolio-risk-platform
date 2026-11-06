package com.riskplatform.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PortfolioDtos {

    public record CreatePortfolioRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String baseCurrency
    ) {}

    public record PortfolioResponse(
            Long id,
            String name,
            String baseCurrency,
            BigDecimal totalMarketValue   // in baseCurrency
    ) {}

    public record AddPositionRequest(
            @NotBlank @Size(max = 50) String instrumentId,
            @NotBlank @Pattern(regexp = "EQUITY|ETF|BOND|FX", message = "must be one of EQUITY, ETF, BOND, FX")
            String instrumentType,
            @NotNull BigDecimal quantity,
            @NotNull @Positive BigDecimal price,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO currency code") String currency,
            @NotNull LocalDate valuationDate
    ) {}

    public record PositionResponse(
            Long id,
            String instrumentId,
            String instrumentType,
            BigDecimal quantity,
            BigDecimal price,
            String currency,              // currency the price is quoted in
            LocalDate valuationDate,
            BigDecimal marketValue,       // quantity * price, in currency
            BigDecimal marketValueBase    // converted to the portfolio's base currency
    ) {}
}
