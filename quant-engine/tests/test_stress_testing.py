"""
Validates stress scenario shocks against hand-computed expected values
for each instrument type, plus the portfolio-level aggregation.
"""

import pytest

from models import StressScenarioRequest, StressPositionInput
from risk.stress_testing import compute_stress_scenario


def test_equity_shock_applies_percentage_directly():
    request = StressScenarioRequest(
        scenario_name="Equity crash",
        equity_shock_pct=-0.20,
        positions=[
            StressPositionInput(instrument_id="AAPL", instrument_type="EQUITY",
                                 quantity=100, current_price=200.0),
        ],
    )
    result = compute_stress_scenario(request)

    impact = result.position_impacts[0]
    assert impact.base_value == pytest.approx(20000.0)
    assert impact.stressed_value == pytest.approx(16000.0)
    assert impact.pnl == pytest.approx(-4000.0)
    assert impact.pnl_pct == pytest.approx(-20.0)


def test_fx_shock_applies_percentage_directly():
    request = StressScenarioRequest(
        scenario_name="USD rally",
        fx_shock_pct=0.05,
        positions=[
            StressPositionInput(instrument_id="USDINR", instrument_type="FX",
                                 quantity=1000, current_price=80.0),
        ],
    )
    result = compute_stress_scenario(request)

    impact = result.position_impacts[0]
    assert impact.base_value == pytest.approx(80000.0)
    assert impact.stressed_value == pytest.approx(84000.0)
    assert impact.pnl == pytest.approx(4000.0)


def test_bond_shock_uses_duration_convexity_approximation():
    # duration=5, convexity=40, rate_shock=+100bps (dy=0.01):
    # pct_change = -5*0.01 + 0.5*40*0.01^2 = -0.05 + 0.002 = -0.048
    request = StressScenarioRequest(
        scenario_name="Rate hike",
        rate_shock_bps=100,
        default_bond_duration=5.0,
        default_bond_convexity=40.0,
        positions=[
            StressPositionInput(instrument_id="UST10Y", instrument_type="BOND",
                                 quantity=1000, current_price=100.0),
        ],
    )
    result = compute_stress_scenario(request)

    impact = result.position_impacts[0]
    assert impact.base_value == pytest.approx(100000.0)
    assert impact.pnl_pct == pytest.approx(-4.8, rel=1e-6)
    assert impact.stressed_value == pytest.approx(95200.0, rel=1e-6)


def test_bond_shock_uses_position_specific_duration_when_provided():
    # Position overrides the scenario default: duration=2, convexity=10
    request = StressScenarioRequest(
        scenario_name="Rate hike",
        rate_shock_bps=100,
        default_bond_duration=5.0,   # should be ignored for this position
        default_bond_convexity=40.0, # should be ignored for this position
        positions=[
            StressPositionInput(instrument_id="SHORT_BOND", instrument_type="BOND",
                                 quantity=100, current_price=100.0,
                                 modified_duration=2.0, convexity=10.0),
        ],
    )
    result = compute_stress_scenario(request)

    # pct_change = -2*0.01 + 0.5*10*0.01^2 = -0.02 + 0.0005 = -0.0195
    impact = result.position_impacts[0]
    assert impact.pnl_pct == pytest.approx(-1.95, rel=1e-6)


def test_portfolio_totals_equal_sum_of_position_impacts():
    request = StressScenarioRequest(
        scenario_name="Mixed shock",
        equity_shock_pct=-0.10,
        fx_shock_pct=0.03,
        positions=[
            StressPositionInput(instrument_id="AAPL", instrument_type="EQUITY",
                                 quantity=100, current_price=150.0),
            StressPositionInput(instrument_id="USDINR", instrument_type="FX",
                                 quantity=500, current_price=80.0),
        ],
    )
    result = compute_stress_scenario(request)

    expected_total_pnl = sum(i.pnl for i in result.position_impacts)
    assert result.total_pnl == pytest.approx(expected_total_pnl, rel=1e-9)
    assert result.stressed_portfolio_value == pytest.approx(
        result.base_portfolio_value + result.total_pnl, rel=1e-9
    )


def test_impacts_sorted_largest_absolute_pnl_first():
    request = StressScenarioRequest(
        scenario_name="Mixed shock",
        equity_shock_pct=-0.05,
        positions=[
            StressPositionInput(instrument_id="SMALL", instrument_type="EQUITY",
                                 quantity=10, current_price=100.0),   # base 1000, pnl -50
            StressPositionInput(instrument_id="BIG", instrument_type="EQUITY",
                                 quantity=1000, current_price=100.0), # base 100000, pnl -5000
        ],
    )
    result = compute_stress_scenario(request)

    assert result.position_impacts[0].instrument_id == "BIG"
    assert result.position_impacts[1].instrument_id == "SMALL"


def test_unrecognized_instrument_type_passes_through_unshocked():
    request = StressScenarioRequest(
        scenario_name="Equity crash",
        equity_shock_pct=-0.50,
        positions=[
            StressPositionInput(instrument_id="COMMODITY_X", instrument_type="COMMODITY",
                                 quantity=10, current_price=100.0),
        ],
    )
    result = compute_stress_scenario(request)

    impact = result.position_impacts[0]
    assert impact.pnl == pytest.approx(0.0)