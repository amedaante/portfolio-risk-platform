"""
Validates fixed-income analytics against hand-computed values using a
2-year, 5% annual coupon par bond (face=100, yield=5%).

Hand calculation:
  Cashflows: year 1 = 5, year 2 = 105
  Price = 5/1.05 + 105/1.05^2 = 4.761905 + 95.238095 = 100.000000 (par bond)
  Macaulay duration = (1*4.761905 + 2*95.238095) / 100 = 1.952381 years
  Modified duration = 1.952381 / 1.05 = 1.859410
  Convexity = [4.761905*1*2 + 95.238095*2*3] / (100 * 1.05^2)
          = 580.952381 / 110.25 = 5.269409  
  DV01 = modified_duration * price * 0.0001 = 1.859410 * 100 * 0.0001 = 0.0185941
"""

import pytest

from models import BondParams
from risk.fixed_income import compute_bond_analytics, compute_rate_shocks, price_bond


def _par_bond():
    return BondParams(
        face_value=100.0,
        coupon_rate=0.05,
        coupon_frequency=1,
        years_to_maturity=2,
        yield_rate=0.05,
    )


def test_par_bond_prices_at_exactly_face_value():
    bond = _par_bond()
    result = compute_bond_analytics(bond)
    assert result.price == pytest.approx(100.0, rel=1e-9)


def test_macaulay_duration_matches_hand_calculation():
    bond = _par_bond()
    result = compute_bond_analytics(bond)
    assert result.macaulay_duration == pytest.approx(1.952381, rel=1e-5)


def test_modified_duration_matches_hand_calculation():
    bond = _par_bond()
    result = compute_bond_analytics(bond)
    assert result.modified_duration == pytest.approx(1.859410, rel=1e-5)


def test_convexity_matches_hand_calculation():
    bond = _par_bond()
    result = compute_bond_analytics(bond)
    assert result.convexity == pytest.approx(5.269409, rel=1e-5)


def test_dv01_matches_hand_calculation():
    bond = _par_bond()
    result = compute_bond_analytics(bond)
    assert result.dv01 == pytest.approx(0.0185941, rel=1e-4)


def test_single_cashflow_bond_duration_equals_maturity():
    # A 1-year zero-coupon-like bond (single cashflow) must have
    # Macaulay duration exactly equal to its maturity -- there's only
    # one cashflow, so the "weighted average time" has nothing to
    # average against.
    bond = BondParams(
        face_value=100.0, coupon_rate=0.05, coupon_frequency=1,
        years_to_maturity=1, yield_rate=0.05,
    )
    result = compute_bond_analytics(bond)
    assert result.macaulay_duration == pytest.approx(1.0, rel=1e-9)


def test_price_decreases_as_yield_increases():
    # Fundamental bond property: price and yield move inversely.
    bond = _par_bond()
    price_at_5pct = price_bond(bond, 0.05)
    price_at_6pct = price_bond(bond, 0.06)
    assert price_at_6pct < price_at_5pct


def test_rate_shock_duration_approximation_is_close_for_small_moves():
    bond = _par_bond()
    response = compute_rate_shocks(bond, shock_bps_list=[10])  # +10bps, a small move

    shock = response.shocks[0]
    # For a small shock, the duration+convexity approximation should
    # be very close to the exact repriced value.
    assert shock.approximation_error == pytest.approx(0.0, abs=0.01)


def test_rate_shock_approximation_error_grows_with_shock_size():
    # Convexity correction matters more for larger yield moves --
    # the duration-only linear approximation increasingly understates
    # the actual (convex) price change as the shock grows.
    bond = _par_bond()
    response = compute_rate_shocks(bond, shock_bps_list=[10, 500])

    small_shock_error = abs(response.shocks[0].approximation_error)
    large_shock_error = abs(response.shocks[1].approximation_error)
    assert large_shock_error > small_shock_error