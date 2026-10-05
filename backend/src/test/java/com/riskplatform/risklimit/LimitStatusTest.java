package com.riskplatform.risklimit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimitStatusTest {

    @Test
    void withinLimitWhenUtilizationBelowWarningThreshold() {
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(LimitStatus.WITHIN, status); // 50% utilization, below 80% warning threshold
    }

    @Test
    void warningWhenUtilizationAtOrAboveThresholdButBelow100() {
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("85"), new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(LimitStatus.WARNING, status); // 85% utilization
    }

    @Test
    void breachedWhenUtilizationAtOrAbove100() {
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(LimitStatus.BREACHED, status); // 120% utilization
    }

    @Test
    void exactlyAtWarningThresholdIsWarningNotWithin() {
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("80"), new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(LimitStatus.WARNING, status); // boundary: exactly 80% counts as WARNING
    }

    @Test
    void exactlyAtLimitIsBreachedNotWarning() {
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(LimitStatus.BREACHED, status); // boundary: exactly 100% counts as BREACHED
    }

    @Test
    void usesAbsoluteValueForSignedCurrentValue() {
        // A negative P&L of -120 against a limit of 100 should still
        // classify as breached -- magnitude matters, not sign.
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("-120"), new BigDecimal("100"), new BigDecimal("80"));
        assertEquals(LimitStatus.BREACHED, status);
    }

    @Test
    void zeroLimitWithNonzeroExposureIsBreached() {
        LimitStatus status = LimitStatus.classify(
                new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("80"));
        assertEquals(LimitStatus.BREACHED, status);
    }

    @Test
    void zeroLimitWithZeroExposureIsWithin() {
        LimitStatus status = LimitStatus.classify(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("80"));
        assertEquals(LimitStatus.WITHIN, status);
    }
}