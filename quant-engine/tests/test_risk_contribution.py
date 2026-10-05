"""
The core correctness property of component VaR is additivity:
contributions must sum exactly to total VaR. This is the property
most worth testing -- a decomposition that doesn't sum correctly is
useless regardless of how plausible the individual numbers look.
"""

import pytest
from datetime import date, timedelta

from models import HistoricalVarRequest, PositionInput, PricePoint
from risk.risk_contribution import compute_risk_contribution


def _position(instrument_id, quantity, seed_returns, start_price):
    prices = [start_price]
    for r in seed_returns:
        prices.append(prices[-1] * (1 + r))
    start = date(2026, 1, 1)
    history = [PricePoint(date=start + timedelta(days=i), price=p) for i, p in enumerate(prices)]
    return PositionInput(instrument_id=instrument_id, quantity=quantity, current_price=prices[-1], price_history=history)


def test_component_var_sums_to_total_var():
    returns_a = [0.01, -0.02, 0.015, -0.01, 0.02, -0.015, 0.01, -0.005, 0.012, -0.008]
    returns_b = [-0.008, 0.012, -0.005, 0.009, -0.011, 0.018, -0.014, 0.007, -0.009, 0.013]

    positions = [
        _position("A", 500, returns_a, 100.0),
        _position("B", 300, returns_b, 50.0),
    ]
    request = HistoricalVarRequest(confidence_level=0.95, horizon_days=1, positions=positions)

    result = compute_risk_contribution(request)

    sum_of_contributions = sum(c.component_var for c in result.contributions)
    assert sum_of_contributions == pytest.approx(result.total_var, rel=1e-9)


def test_pct_contributions_sum_to_roughly_100():
    returns_a = [0.01, -0.02, 0.015, -0.01, 0.02, -0.015, 0.01, -0.005, 0.012, -0.008]
    returns_b = [-0.008, 0.012, -0.005, 0.009, -0.011, 0.018, -0.014, 0.007, -0.009, 0.013]

    positions = [
        _position("A", 500, returns_a, 100.0),
        _position("B", 300, returns_b, 50.0),
    ]
    request = HistoricalVarRequest(confidence_level=0.95, horizon_days=1, positions=positions)
    result = compute_risk_contribution(request)

    total_pct = sum(c.pct_of_total_var for c in result.contributions)
    assert total_pct == pytest.approx(100.0, rel=1e-6)


def test_raises_on_zero_volatility_single_flat_position():
    flat_history = [date(2026, 1, i + 1) for i in range(5)]
    history = [PricePoint(date=d, price=100.0) for d in flat_history]  # no price movement at all
    position = PositionInput(instrument_id="FLAT", quantity=100, current_price=100.0, price_history=history)
    request = HistoricalVarRequest(confidence_level=0.95, horizon_days=1, positions=[position])

    with pytest.raises(ValueError):
        compute_risk_contribution(request)