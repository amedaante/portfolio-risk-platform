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

# ---- FX shock with a base currency ------------------------------------------
# fx_shock_pct = +5% means "USD +5% against every other currency" for a USD
# book. Previously every FX position simply gained 5%.

def _usd_book(*positions, fx_shock_pct=0.05, equity_shock_pct=0.0):
    return compute_stress_scenario(StressScenarioRequest(
        scenario_name="USD +5%", base_currency="USD",
        fx_shock_pct=fx_shock_pct, equity_shock_pct=equity_shock_pct,
        positions=list(positions),
    ))


def test_usd_strength_helps_long_usdinr():
    # 50,000 USD notional (current price already converted to USD)
    result = _usd_book(StressPositionInput(instrument_id="USDINR", instrument_type="FX",
                                           price_currency="INR", quantity=50000, current_price=1.0))
    assert result.total_pnl == pytest.approx(50000 * 0.05)


def test_usd_strength_hurts_long_eurusd():
    result = _usd_book(StressPositionInput(instrument_id="EURUSD", instrument_type="FX",
                                           price_currency="USD", quantity=1000, current_price=1.10))
    assert result.total_pnl == pytest.approx(1100 * (1 / 1.05 - 1))
    assert result.total_pnl < 0


def test_cross_pair_is_unaffected_by_a_uniform_usd_move():
    result = _usd_book(StressPositionInput(instrument_id="EURGBP", instrument_type="FX",
                                           price_currency="GBP", quantity=1000, current_price=1.2))
    assert result.total_pnl == pytest.approx(0.0)


def test_foreign_equity_takes_price_and_translation_shocks():
    # A EUR-priced equity worth 10,000 USD, equities -10%, USD +5%
    result = _usd_book(StressPositionInput(instrument_id="SAP", instrument_type="EQUITY",
                                           price_currency="EUR", quantity=100, current_price=100.0),
                       equity_shock_pct=-0.10)
    assert result.stressed_portfolio_value == pytest.approx(10000 * 0.90 / 1.05)


def test_base_currency_equity_ignores_fx_shock():
    result = _usd_book(StressPositionInput(instrument_id="AAPL", instrument_type="EQUITY",
                                           price_currency="USD", quantity=10, current_price=200.0))
    assert result.total_pnl == pytest.approx(0.0)
