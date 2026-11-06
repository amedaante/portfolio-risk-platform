"""
Regression tests for input-handling bugs found in review:
- two positions in the same instrument were mis-valued
- out-of-range parameters produced inf/NaN (HTTP 500) instead of a 422
- a non-positive-definite covariance crashed Monte Carlo with LinAlgError
"""

import numpy as np
import pytest
from datetime import date, timedelta
from pydantic import ValidationError

from models import HistoricalVarRequest, MonteCarloVarRequest, PositionInput, PricePoint
from risk.historical_simulation import compute_historical_var
from risk.monte_carlo import compute_monte_carlo_var, correlating_matrix
from risk.parametric import compute_parametric_var
from risk.returns import build_returns_matrix

RETURNS = [0.01, -0.02, 0.015, -0.01, 0.02, -0.015, 0.01, -0.005, 0.012, -0.008]


def _position(instrument_id, quantity, returns=RETURNS, start_price=100.0):
    prices = [start_price]
    for r in returns:
        prices.append(prices[-1] * (1 + r))
    start = date(2026, 1, 1)
    history = [PricePoint(date=start + timedelta(days=i), price=p) for i, p in enumerate(prices)]
    return PositionInput(instrument_id=instrument_id, quantity=quantity,
                         current_price=prices[-1], price_history=history)


# ---- duplicate instruments ----------------------------------------------

def test_two_lots_of_the_same_instrument_equal_one_combined_position():
    # Old behaviour: the exposures dict kept only the last lot (300), and the
    # duplicated return column then counted it twice -> modelled as 600, not 400.
    two_lots = HistoricalVarRequest(confidence_level=0.95, positions=[_position("AAPL", 100), _position("AAPL", 300)])
    one_lot = HistoricalVarRequest(confidence_level=0.95, positions=[_position("AAPL", 400)])

    assert compute_parametric_var(two_lots).var == pytest.approx(compute_parametric_var(one_lot).var, rel=1e-12)
    assert compute_historical_var(two_lots).portfolio_value == pytest.approx(
        compute_historical_var(one_lot).portfolio_value, rel=1e-12)


def test_returns_matrix_has_one_column_per_instrument():
    matrix = build_returns_matrix([_position("AAPL", 100), _position("AAPL", 300), _position("SPY", 10)], "test")
    assert matrix.instruments == ["AAPL", "SPY"]
    assert matrix.exposures[0] == pytest.approx(400 * _position("AAPL", 1).current_price)


# ---- parameter bounds -----------------------------------------------------

@pytest.mark.parametrize("confidence", [1.0, 0.5, 1.5, 0.0])
def test_confidence_level_outside_half_to_one_is_rejected(confidence):
    with pytest.raises(ValidationError):
        HistoricalVarRequest(confidence_level=confidence, positions=[_position("X", 1)])


def test_zero_horizon_and_empty_portfolio_are_rejected():
    with pytest.raises(ValidationError):
        HistoricalVarRequest(horizon_days=0, positions=[_position("X", 1)])
    with pytest.raises(ValidationError):
        HistoricalVarRequest(positions=[])


def test_simulation_count_is_capped():
    with pytest.raises(ValidationError):
        MonteCarloVarRequest(num_simulations=50_000_000, positions=[_position("X", 1)])


def test_non_positive_price_is_rejected():
    with pytest.raises(ValidationError):
        PricePoint(date=date(2026, 1, 1), price=0.0)


def test_camel_case_json_is_validated_too():
    with pytest.raises(ValidationError):
        HistoricalVarRequest.model_validate({"confidenceLevel": 1.0, "positions": [_position("X", 1).model_dump(by_alias=True)]})


# ---- Monte Carlo robustness -------------------------------------------------

def test_correlating_matrix_reproduces_a_positive_definite_covariance():
    cov = np.array([[0.04, 0.012], [0.012, 0.09]])
    L = correlating_matrix(cov)
    assert np.allclose(L @ L.T, cov, atol=1e-9)


def test_correlating_matrix_falls_back_instead_of_crashing_on_indefinite_input():
    # Eigenvalues 3 and -1: Cholesky fails even with jitter. The fallback
    # clips -1 to 0, giving the nearest PSD matrix in the eigenbasis.
    cov = np.array([[1.0, 2.0], [2.0, 1.0]])
    L = correlating_matrix(cov)
    assert np.allclose(L @ L.T, [[1.5, 1.5], [1.5, 1.5]])


def test_monte_carlo_handles_perfectly_collinear_instruments():
    # Same return series under two names -> singular covariance matrix.
    request = MonteCarloVarRequest(
        confidence_level=0.95, num_simulations=20000, random_seed=7,
        positions=[_position("A", 100), _position("B", 100)],
    )
    mc = compute_monte_carlo_var(request)
    param = compute_parametric_var(HistoricalVarRequest(confidence_level=0.95, positions=request.positions))
    assert mc.var == pytest.approx(param.var, rel=0.05)


def test_monte_carlo_is_zero_mean_like_parametric():
    # Strong positive drift in the sample. With the mean included, MC VaR
    # sat well below parametric; at zero mean they estimate the same thing.
    drifting = [0.03, 0.01, 0.04, 0.0, 0.035, 0.005, 0.03, 0.01, 0.04, 0.0] * 3
    positions = [_position("UP", 100, returns=drifting)]
    mc = compute_monte_carlo_var(MonteCarloVarRequest(
        confidence_level=0.99, num_simulations=200_000, random_seed=1, positions=positions))
    param = compute_parametric_var(HistoricalVarRequest(confidence_level=0.99, positions=positions))
    assert mc.var == pytest.approx(param.var, rel=0.02)
