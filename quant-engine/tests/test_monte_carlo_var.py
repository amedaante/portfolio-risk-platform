"""
Monte Carlo VaR can't be hand-traced exactly (it's random simulation),
so these tests validate the properties that MUST hold for the
implementation to be correct: reproducibility given a fixed seed, and
convergence toward the parametric closed-form value as simulation
count increases (both estimate the same underlying normal-distribution
VaR, so they should agree closely at high simulation counts).
"""

import pytest
from datetime import date, timedelta

from models import MonteCarloVarRequest, HistoricalVarRequest, PositionInput, PricePoint
from risk.monte_carlo import compute_monte_carlo_var
from risk.parametric import compute_parametric_var


def _build_request(num_simulations, random_seed, confidence_level=0.95):
    returns = [0.012, -0.018, 0.009, -0.006, 0.021, -0.014, 0.008, -0.011, 0.016, -0.007] * 5
    prices = [100.0]
    for r in returns:
        prices.append(prices[-1] * (1 + r))

    start = date(2026, 1, 1)
    history = [PricePoint(date=start + timedelta(days=i), price=p) for i, p in enumerate(prices)]
    position = PositionInput(instrument_id="X", quantity=100, current_price=prices[-1], price_history=history)

    return MonteCarloVarRequest(
        confidence_level=confidence_level,
        horizon_days=1,
        num_simulations=num_simulations,
        random_seed=random_seed,
        positions=[position],
    ), HistoricalVarRequest(
        confidence_level=confidence_level,
        horizon_days=1,
        positions=[position],
    )


def test_same_seed_produces_identical_results():
    mc_request, _ = _build_request(num_simulations=5000, random_seed=42)

    result_1 = compute_monte_carlo_var(mc_request)
    result_2 = compute_monte_carlo_var(mc_request)

    assert result_1.var == result_2.var
    assert result_1.expected_shortfall == result_2.expected_shortfall


def test_different_seeds_produce_different_results():
    mc_request_a, _ = _build_request(num_simulations=5000, random_seed=1)
    mc_request_b, _ = _build_request(num_simulations=5000, random_seed=2)

    result_a = compute_monte_carlo_var(mc_request_a)
    result_b = compute_monte_carlo_var(mc_request_b)

    assert result_a.var != result_b.var


def test_converges_toward_parametric_var_at_high_simulation_count():
    mc_request, param_request = _build_request(num_simulations=50000, random_seed=42)

    mc_result = compute_monte_carlo_var(mc_request)
    param_result = compute_parametric_var(param_request)

    # Both estimate VaR under a normal-distribution assumption, so at
    # high simulation counts they should agree within ~5%.
    assert mc_result.var == pytest.approx(param_result.var, rel=0.05)