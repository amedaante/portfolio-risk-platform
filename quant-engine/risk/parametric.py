"""
Parametric (variance-covariance) VaR and Expected Shortfall.

Assumes portfolio P&L is normally distributed. Portfolio variance is
computed analytically from the covariance matrix of instrument returns
and current dollar exposures: Var(P&L) = e^T * Sigma * e, where e is
the vector of dollar exposures and Sigma is the covariance matrix of
instrument returns.

Known limitation (documented in risk-methodology.md): assumes normality,
so it will understate tail risk relative to Historical/Monte Carlo VaR
whenever real returns are fat-tailed or skewed — which is the normal
case for equities. That gap is itself a useful thing to show on the
dashboard (Parametric vs Historical VaR side by side).
"""

import numpy as np
import pandas as pd
from scipy.stats import norm

from models import HistoricalVarRequest, ParametricVarResponse


def compute_parametric_var(request: HistoricalVarRequest) -> ParametricVarResponse:
    position_frames = []
    exposures = {}

    for position in request.positions:
        df = pd.DataFrame(
            [(p.date, p.price) for p in position.price_history],
            columns=["date", "price"],
        ).set_index("date").sort_index()

        returns = df["price"].pct_change().dropna()
        returns.name = position.instrument_id
        position_frames.append(returns)

        exposures[position.instrument_id] = position.quantity * position.current_price

    returns_df = pd.concat(position_frames, axis=1, join="inner")
    if returns_df.empty or len(returns_df) < 2:
        raise ValueError(
            "Not enough overlapping price history across positions to compute parametric VaR"
        )

    exposure_vector = pd.Series(exposures).reindex(returns_df.columns).to_numpy()
    portfolio_value = float(exposure_vector.sum())

    # Sample covariance matrix of daily returns (instrument x instrument)
    cov_matrix = returns_df.cov().to_numpy()

    # Portfolio P&L variance in dollar terms: e^T * Sigma * e
    portfolio_variance = float(exposure_vector @ cov_matrix @ exposure_vector.T)
    portfolio_stddev = float(np.sqrt(max(portfolio_variance, 0.0)))

    confidence = request.confidence_level
    z = float(norm.ppf(confidence))

    # Closed-form expected shortfall for a normal distribution:
    # ES = sigma * phi(z) / (1 - confidence), where phi is the standard normal PDF
    es_multiplier = norm.pdf(z) / (1 - confidence)

    scale = np.sqrt(request.horizon_days)
    var_scaled = portfolio_stddev * z * scale
    es_scaled = portfolio_stddev * es_multiplier * scale

    return ParametricVarResponse(
        methodology="PARAMETRIC",
        confidence_level=confidence,
        horizon_days=request.horizon_days,
        portfolio_value=portfolio_value,
        var=var_scaled,
        expected_shortfall=es_scaled,
        observation_count=len(returns_df),
        portfolio_volatility_1day=portfolio_stddev,
        z_score=z,
    )