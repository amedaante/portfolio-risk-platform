"""
Multi-asset stress testing: applies scenario shocks to a portfolio and
compares base vs. stressed value, both at the portfolio level and
per-position.

Shock application by instrument type:
- EQUITY / ETF: direct percentage price shock
- BOND: rate shock via duration + convexity approximation (same
  formula as risk/fixed_income.py's rate_shock, but using a
  caller-supplied or default duration/convexity rather than deriving
  it from full bond cashflows -- positions don't carry coupon/maturity
  metadata, so exact repricing isn't available here)
- FX: see below

FX shock semantics (when the request names a base_currency):
fx_shock_pct is the move of the portfolio's base currency against every
other currency, e.g. +0.05 = "USD +5%" for a USD portfolio.
- FX pair XXXYYY, priced as YYY per XXX:
    XXX is the base currency  -> pair rate rises:  x (1 + s)
    YYY is the base currency  -> pair rate falls:  x 1 / (1 + s)
    neither (a cross pair)    -> both legs move together: unchanged
- Any other position priced in a foreign currency loses value when
  translated back into the stronger base currency: x 1 / (1 + s).
The previous version applied +s to every FX position, so "USD +5%" made
both USDINR and EURUSD gain, though a long EURUSD position loses.

Without a base_currency, the legacy rule applies: FX positions x (1 + s).

Unrecognized instrument types pass through unshocked (0% impact),
rather than silently failing the whole scenario.
"""

from models import StressScenarioRequest, StressScenarioResponse, StressPositionImpact


def _fx_pair_factor(instrument_id: str, base_currency: str | None, fx_shock: float) -> float:
    if base_currency is None:
        return 1 + fx_shock
    pair = instrument_id.upper()
    if len(pair) == 6 and pair.isalpha():
        if pair[:3] == base_currency:
            return 1 + fx_shock
        if pair[3:] == base_currency:
            return 1 / (1 + fx_shock)
    return 1.0


def _translation_factor(price_currency: str | None, base_currency: str | None, fx_shock: float) -> float:
    if base_currency is None or price_currency is None or price_currency == base_currency:
        return 1.0
    return 1 / (1 + fx_shock)


def compute_stress_scenario(request: StressScenarioRequest) -> StressScenarioResponse:
    impacts = []
    base_total = 0.0
    stressed_total = 0.0
    base_ccy = request.base_currency.upper() if request.base_currency else None

    for position in request.positions:
        base_value = position.quantity * position.current_price
        instrument_type = position.instrument_type.upper()
        translation = _translation_factor(position.price_currency, base_ccy, request.fx_shock_pct)

        if instrument_type in ("EQUITY", "ETF"):
            stressed_value = base_value * (1 + request.equity_shock_pct) * translation

        elif instrument_type == "BOND":
            duration = position.modified_duration if position.modified_duration is not None \
                else request.default_bond_duration
            convexity = position.convexity if position.convexity is not None \
                else request.default_bond_convexity

            dy = request.rate_shock_bps / 10000.0
            pct_change = -duration * dy + 0.5 * convexity * dy ** 2
            stressed_value = base_value * (1 + pct_change) * translation

        elif instrument_type == "FX":
            # The pair rate is itself the FX risk; no separate translation.
            stressed_value = base_value * _fx_pair_factor(position.instrument_id, base_ccy, request.fx_shock_pct)

        else:
            stressed_value = base_value  # unrecognized type: no shock applied

        pnl = stressed_value - base_value
        pnl_pct = (pnl / base_value * 100) if base_value != 0 else 0.0

        impacts.append(StressPositionImpact(
            instrument_id=position.instrument_id,
            instrument_type=instrument_type,
            base_value=base_value,
            stressed_value=stressed_value,
            pnl=pnl,
            pnl_pct=pnl_pct,
        ))

        base_total += base_value
        stressed_total += stressed_value

    # Largest-impact-first, matching the ordering convention from
    # risk contribution -- the point of this output is "what hurts most."
    impacts.sort(key=lambda i: abs(i.pnl), reverse=True)

    total_pnl = stressed_total - base_total
    total_pnl_pct = (total_pnl / base_total * 100) if base_total != 0 else 0.0

    return StressScenarioResponse(
        scenario_name=request.scenario_name,
        equity_shock_pct=request.equity_shock_pct,
        rate_shock_bps=request.rate_shock_bps,
        fx_shock_pct=request.fx_shock_pct,
        base_portfolio_value=base_total,
        stressed_portfolio_value=stressed_total,
        total_pnl=total_pnl,
        total_pnl_pct=total_pnl_pct,
        position_impacts=impacts,
    )
