"""
Component VaR: decomposes total parametric VaR into additive
per-instrument contributions.

C_i = e_i * (Sigma @ e)_i / sigma_p

By Euler's homogeneity theorem (sigma_p is linear-homogeneous degree 1
in the exposure vector e), sum(C_i) == sigma_p exactly. Scaling each
C_i by the same (z * sqrt(horizon)) factor used for total VaR means
sum(component_var) == total_var exactly; test_component_var_sums_to_total_var
checks this identity.
"""

import numpy as np
from scipy.stats import norm

from models import HistoricalVarRequest, RiskContributionItem, RiskContributionResponse
from risk.returns import build_returns_matrix, portfolio_stddev


def compute_risk_contribution(request: HistoricalVarRequest) -> RiskContributionResponse:
    matrix = build_returns_matrix(request.positions, "risk contribution")
    instruments = matrix.instruments
    exposure_vector = matrix.exposures

    cov_matrix = matrix.returns.cov().to_numpy()
    sigma_p = portfolio_stddev(cov_matrix, exposure_vector)

    if sigma_p == 0.0:
        raise ValueError("Portfolio volatility is zero; cannot decompose risk contribution")

    sigma_e = cov_matrix @ exposure_vector  # (Sigma @ e), vector over instruments
    component_vol = exposure_vector * sigma_e / sigma_p  # sums exactly to sigma_p

    confidence = request.confidence_level
    z = float(norm.ppf(confidence))
    scale = np.sqrt(request.horizon_days)

    total_var = sigma_p * z * scale
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
        portfolio_value=matrix.portfolio_value,
        total_var=total_var,
        contributions=contributions,
    )
