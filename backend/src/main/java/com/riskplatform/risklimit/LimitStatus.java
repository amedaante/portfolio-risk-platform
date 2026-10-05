package com.riskplatform.risklimit;

import java.math.BigDecimal;

public enum LimitStatus {
    WITHIN, WARNING, BREACHED;

    /**
     * Pure classification logic, deliberately kept free of any Spring
     * dependency so it can be unit tested directly.
     *
     * utilizationPct = currentValue / limitValue * 100 (using absolute
     * value of currentValue, since exposures and VaR are reported as
     * non-negative magnitudes but a caller could pass a signed P&L).
     */
    public static LimitStatus classify(BigDecimal currentValue, BigDecimal limitValue, BigDecimal warningThresholdPct) {
        if (limitValue.compareTo(BigDecimal.ZERO) == 0) {
            // A zero limit is a degenerate/misconfigured case; treat
            // any nonzero exposure against it as breached rather than
            // dividing by zero.
            return currentValue.abs().compareTo(BigDecimal.ZERO) > 0 ? BREACHED : WITHIN;
        }

        BigDecimal utilizationPct = currentValue.abs()
                .divide(limitValue.abs(), 6, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (utilizationPct.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return BREACHED;
        }
        if (utilizationPct.compareTo(warningThresholdPct) >= 0) {
            return WARNING;
        }
        return WITHIN;
    }
}