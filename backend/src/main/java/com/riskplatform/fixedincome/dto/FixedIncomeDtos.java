package com.riskplatform.fixedincome.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public class FixedIncomeDtos {

    public record BondParams(
            @NotNull @Positive BigDecimal faceValue,
            @NotNull BigDecimal couponRate,
            @NotNull @Positive Integer couponFrequency,
            @NotNull @Positive BigDecimal yearsToMaturity,
            @NotNull BigDecimal yieldRate
    ) {}

    public record BondAnalyticsResponse(
            BigDecimal price,
            BigDecimal macaulayDuration,
            BigDecimal modifiedDuration,
            BigDecimal convexity,
            BigDecimal dv01
    ) {}

    public record RateShockRequest(
            @NotNull BondParams bond,
            List<Integer> shockBps
    ) {}

    public record RateShockResult(
            Integer shockBps,
            BigDecimal shockedYield,
            BigDecimal exactPrice,
            BigDecimal exactPriceChange,
            BigDecimal approximatedPriceChange,
            BigDecimal approximationError
    ) {}

    public record RateShockResponse(
            BigDecimal basePrice,
            BigDecimal baseYield,
            BigDecimal modifiedDuration,
            BigDecimal convexity,
            List<RateShockResult> shocks
    ) {}
}