"""
Validates Parametric VaR against independently computed closed-form
values. The expected numbers here are computed directly with
numpy/scipy in this test file -- NOT by importing the production
compute_parametric_var logic -- so a bug in the implementation can't
accidentally match a bug in the test.
"""

import numpy as np
import pytest
from scipy.stats import norm
from datetime import date, timedelta

from models import HistoricalVarRequest, PositionInput, PricePoint
from risk.parametric import compute_parametric_var


def test_parametric_var_matches_independently_computed_closed_form():
    returns = [-0.05, -0.03, -0.02, -0.01, 0.00, 0.01, 0.02, 0.03, 0.04, 0.05]
    prices = [100.0]
    for r in returns:
        prices.append(prices[-1] * (1 + r))

    start = date(2026, 1, 1)
    history = [
        PricePoint(date=start + timedelta(days=i), price=p)
        for i, p in enumerate(prices)
    ]

    exposure = 100 * prices[-1]
    position = PositionInput(
        instrument_id="TEST", quantity=100, current_price=prices[-1], price_history=history
    )
    request = HistoricalVarRequest(confidence_level=0.90, horizon_days=1, positions=[position])

    # --- Independent expected-value computation ---
    returns_array = np.array(returns)
    sample_std = returns_array.std(ddof=1)  # pandas .cov()/.std() default to ddof=1
    expected_portfolio_stddev = exposure * sample_std
    z = norm.ppf(0.90)
    expected_var = expected_portfolio_stddev * z
    expected_es = expected_portfolio_stddev * norm.pdf(z) / (1 - 0.90)

    # --- Actual implementation output ---
    result = compute_parametric_var(request)

    assert result.portfolio_volatility_1day == pytest.approx(expected_portfolio_stddev, rel=1e-6)
    assert result.var == pytest.approx(expected_var, rel=1e-6)
    assert result.expected_shortfall == pytest.approx(expected_es, rel=1e-6)
    assert result.z_score == pytest.approx(z, rel=1e-9)


def test_parametric_es_always_exceeds_var_for_same_confidence():
    returns = [0.01, -0.02, 0.015, -0.01, 0.005, -0.008, 0.012, -0.015, 0.02, -0.01]
    prices = [50.0]
    for r in returns:
        prices.append(prices[-1] * (1 + r))

    start = date(2026, 1, 1)
    history = [PricePoint(date=start + timedelta(days=i), price=p) for i, p in enumerate(prices)]
    position = PositionInput(instrument_id="X", quantity=200, current_price=prices[-1], price_history=history)
    request = HistoricalVarRequest(confidence_level=0.99, horizon_days=1, positions=[position])

    result = compute_parametric_var(request)
    assert result.expected_shortfall > result.var