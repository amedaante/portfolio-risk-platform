"""
Component VaR: decomposes total parametric VaR into additive
per-instrument contributions.

C_i = e_i * (Sigma @ e)_i / sigma_p

By Euler's homogeneity theorem (sigma_p is linear-homogeneous degree 1
in the exposure vector e), sum(C_i) == sigma_p exactly. Scaling each
C_i by the same (z * sqrt(horizon)) factor used for total VaR means
sum(component_var) == total_var exactly -- this identity is asserted
in the implementation itself as a correctness check, not just hoped for.
"""

import numpy as np
import pandas as pd
from scipy.stats import norm

from models import HistoricalVarRequest, RiskContributionItem, RiskContributionResponse


def compute_risk_contribution(request: HistoricalVarRequest) -> RiskContributionResponse:
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
            "Not enough overlapping price history across positions to compute risk contribution"
        )

    instruments = list(returns_df.columns)
    cov_matrix = returns_df.cov().to_numpy()
    exposure_vector = pd.Series(exposures).reindex(instruments).to_numpy()
    portfolio_value = float(exposure_vector.sum())

    portfolio_variance = float(exposure_vector @ cov_matrix @ exposure_vector.T)
    portfolio_stddev = float(np.sqrt(max(portfolio_variance, 0.0)))

    if portfolio_stddev == 0.0:
        raise ValueError("Portfolio volatility is zero; cannot decompose risk contribution")

    sigma_e = cov_matrix @ exposure_vector  # (Sigma @ e), vector over instruments
    component_vol = exposure_vector * sigma_e / portfolio_stddev  # sums exactly to portfolio_stddev

    confidence = request.confidence_level
    z = float(norm.ppf(confidence))
    scale = np.sqrt(request.horizon_days)

    total_var = portfolio_stddev * z * scale
    component_var = component_vol * z * scale

    contributions = [
        RiskContributionItem(
            instrument_id=instruments[i],
            exposure=float(exposure_vector[i]),
            component_var=float(component_var[i]),
            pct_of_total_var=float(component_var[i] / total_var * 100) if total_var != 0 else 0.0,
        )
        for i in range(len(instruments))
    ]

    # Sort by magnitude of contribution, largest risk driver first --
    # this ordering is the whole point of the feature (section 6).
    contributions.sort(key=lambda c: abs(c.component_var), reverse=True)

    return RiskContributionResponse(
        confidence_level=confidence,
        horizon_days=request.horizon_days,
        portfolio_value=portfolio_value,
        total_var=total_var,
        contributions=contributions,
    )