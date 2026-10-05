"""
Fixed-income analytics: bond pricing, Macaulay/modified duration,
convexity, and DV01/PV01, plus rate-shock scenario analysis.

All formulas are closed-form (no simulation), using standard
fixed-income conventions (Fabozzi). Cashflow timing uses periods
(1/frequency years each), converted to years for duration/convexity.
"""

import numpy as np

from models import BondParams, BondAnalyticsResponse, RateShockResult, RateShockResponse


def _cashflows(params: BondParams):
    """Returns (period_index, cashflow) pairs, 1-indexed periods."""
    coupon_per_period = params.face_value * params.coupon_rate / params.coupon_frequency
    n_periods = round(params.years_to_maturity * params.coupon_frequency)

    cashflows = []
    for t in range(1, n_periods + 1):
        cf = coupon_per_period
        if t == n_periods:
            cf += params.face_value
        cashflows.append((t, cf))
    return cashflows


def price_bond(params: BondParams, yield_rate: float) -> float:
    """Closed-form discounted cashflow price at a given annual yield."""
    f = params.coupon_frequency
    period_rate = yield_rate / f

    price = 0.0
    for t, cf in _cashflows(params):
        price += cf / (1 + period_rate) ** t
    return price


def compute_bond_analytics(params: BondParams) -> BondAnalyticsResponse:
    f = params.coupon_frequency
    period_rate = params.yield_rate / f

    price = price_bond(params, params.yield_rate)
    if price <= 0:
        raise ValueError("Computed bond price is non-positive; check input parameters")

    cashflows = _cashflows(params)

    # Macaulay duration: PV-weighted average time to cashflow, in years
    weighted_time_sum = 0.0
    for t, cf in cashflows:
        pv = cf / (1 + period_rate) ** t
        time_years = t / f
        weighted_time_sum += time_years * pv
    macaulay_duration = weighted_time_sum / price

    modified_duration = macaulay_duration / (1 + period_rate)

    # Convexity: sum( PV_t * t_years * (t_years + 1/f) ) / (Price * (1+period_rate)^2)
    convexity_sum = 0.0
    for t, cf in cashflows:
        pv = cf / (1 + period_rate) ** t
        time_years = t / f
        convexity_sum += pv * time_years * (time_years + 1 / f)
    convexity = convexity_sum / (price * (1 + period_rate) ** 2)

    # DV01: dollar price change for a 1bp (0.0001) yield move,
    # via the modified-duration linear approximation.
    dv01 = modified_duration * price * 0.0001

    return BondAnalyticsResponse(
        price=price,
        macaulay_duration=macaulay_duration,
        modified_duration=modified_duration,
        convexity=convexity,
        dv01=dv01,
    )


def compute_rate_shocks(params: BondParams, shock_bps_list: list[int]) -> RateShockResponse:
    base_price = price_bond(params, params.yield_rate)
    analytics = compute_bond_analytics(params)

    results = []
    for shock_bps in shock_bps_list:
        delta_y = shock_bps / 10000.0  # bps -> decimal yield change
        shocked_yield = params.yield_rate + delta_y

        exact_price = price_bond(params, shocked_yield)
        exact_change = exact_price - base_price

        # Duration + convexity approximation:
        # dP/P approx -ModDur * dy + 0.5 * Convexity * dy^2
        approx_pct_change = (
            -analytics.modified_duration * delta_y
            + 0.5 * analytics.convexity * delta_y ** 2
        )
        approx_change = approx_pct_change * base_price

        results.append(RateShockResult(
            shock_bps=shock_bps,
            shocked_yield=shocked_yield,
            exact_price=exact_price,
            exact_price_change=exact_change,
            approximated_price_change=approx_change,
            approximation_error=exact_change - approx_change,
        ))

    return RateShockResponse(
        base_price=base_price,
        base_yield=params.yield_rate,
        modified_duration=analytics.modified_duration,
        convexity=analytics.convexity,
        shocks=results,
    )