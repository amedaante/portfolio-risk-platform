"""
Validates Historical VaR against hand-traceable numbers.

Single instrument, quantity=100, price=100 -> exposure = $10,000.
Returns are chosen as a clean arithmetic sequence so the dollar P&L
distribution is exactly: [-500,-300,-200,-100,0,100,200,300,400,500].

At 90% confidence (tail_quantile = 0.10), numpy's default linear
interpolation percentile method gives an exact, hand-computable answer:
  index = (n-1) * 0.10 = 9 * 0.10 = 0.9
  value = sorted[0] + 0.9 * (sorted[1] - sorted[0])
        = -500 + 0.9 * (-300 - (-500))
        = -500 + 0.9 * 200
        = -320
  => VaR = 320 (loss magnitude, positive sign)
"""

import pytest
from datetime import date, timedelta

from models import HistoricalVarRequest, PositionInput, PricePoint
from risk.historical_simulation import compute_historical_var


def _build_single_instrument_request(confidence_level=0.90, horizon_days=1):
    # 11 prices -> 10 pct_change returns, chosen so returns are exactly
    # [-0.05, -0.03, -0.02, -0.01, 0.00, 0.01, 0.02, 0.03, 0.04, 0.05]
    returns = [-0.05, -0.03, -0.02, -0.01, 0.00, 0.01, 0.02, 0.03, 0.04, 0.05]
    prices = [100.0]
    for r in returns:
        prices.append(prices[-1] * (1 + r))

    start = date(2026, 1, 1)
    history = [
        PricePoint(date=start + timedelta(days=i), price=p)
        for i, p in enumerate(prices)
    ]

    # current_price is deliberately fixed at 100.0, independent of the
    # compounded price_history -- the API treats these as separate
    # inputs (current_price = today's mark, price_history = return
    # series), so exposure here is exactly 100 * 100 = $10,000,
    # matching the hand-computed values in the test docstrings.
    position = PositionInput(
        instrument_id="TEST",
        quantity=100,
        current_price=100.0,
        price_history=history,
    )

    return HistoricalVarRequest(
        confidence_level=confidence_level,
        horizon_days=horizon_days,
        positions=[position],
    )


def test_historical_var_matches_hand_computed_value():
    request = _build_single_instrument_request(confidence_level=0.90, horizon_days=1)
    result = compute_historical_var(request)

    assert result.observation_count == 10
    assert result.portfolio_value == pytest.approx(10000.0, rel=1e-9)
    assert result.var == pytest.approx(320.0, rel=1e-6)

def test_expected_shortfall_averages_tail_beyond_var():
    # At 90% confidence with 10 observations, the single worst outcome
    # (-500) is the only point strictly in the tail beyond the VaR
    # threshold of -320, so ES should equal exactly 500.
    request = _build_single_instrument_request(confidence_level=0.90, horizon_days=1)
    result = compute_historical_var(request)

    assert result.expected_shortfall == pytest.approx(500.0, rel=1e-6)


def test_expected_shortfall_never_less_than_var():
    request = _build_single_instrument_request(confidence_level=0.95, horizon_days=1)
    result = compute_historical_var(request)

    assert result.expected_shortfall >= result.var


def test_horizon_scaling_applies_square_root_of_time():
    request_1day = _build_single_instrument_request(confidence_level=0.90, horizon_days=1)
    request_10day = _build_single_instrument_request(confidence_level=0.90, horizon_days=10)

    result_1day = compute_historical_var(request_1day)
    result_10day = compute_historical_var(request_10day)

    assert result_10day.var == pytest.approx(result_1day.var * (10 ** 0.5), rel=1e-6)


def test_raises_on_insufficient_overlapping_history():
    start = date(2026, 1, 1)
    position = PositionInput(
        instrument_id="TEST",
        quantity=100,
        current_price=100.0,
        price_history=[PricePoint(date=start, price=100.0)],  # only 1 point -> 0 returns
    )
    request = HistoricalVarRequest(confidence_level=0.95, horizon_days=1, positions=[position])

    with pytest.raises(ValueError):
        compute_historical_var(request)