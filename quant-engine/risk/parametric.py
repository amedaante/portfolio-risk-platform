"""
Parametric (variance-covariance) VaR and Expected Shortfall.

Assumes portfolio P&L is normally distributed with zero mean. Portfolio
variance is computed analytically from the covariance matrix of
instrument returns and current dollar exposures: Var(P&L) = e^T * Sigma * e,
where e is the vector of dollar exposures and Sigma is the covariance
matrix of instrument returns.

Known limitation (documented in Risk-Methodology.md): assumes normality,
so it will understate tail risk relative to Historical VaR whenever real
returns are fat-tailed or skewed — which is the normal case for equities.
"""

import numpy as np
from scipy.stats import norm

from models import HistoricalVarRequest, ParametricVarResponse
from risk.returns import build_returns_matrix, portfolio_stddev


def compute_parametric_var(request: HistoricalVarRequest) -> ParametricVarResponse:
    matrix = build_returns_matrix(request.positions, "parametric VaR")

    # Sample covariance matrix of daily returns (instrument x instrument)
    cov_matrix = matrix.returns.cov().to_numpy()

    # Portfolio P&L standard deviation in currency terms: sqrt(e^T * Sigma * e)
    sigma_p = portfolio_stddev(cov_matrix, matrix.exposures)

    confidence = request.confidence_level
    z = float(norm.ppf(confidence))

    # Closed-form expected shortfall for a normal distribution:
    # ES = sigma * phi(z) / (1 - confidence), where phi is the standard normal PDF
    es_multiplier = norm.pdf(z) / (1 - confidence)

    scale = np.sqrt(request.horizon_days)

    return ParametricVarResponse(
        methodology="PARAMETRIC",
        confidence_level=confidence,
        horizon_days=request.horizon_days,
        portfolio_value=matrix.portfolio_value,
        var=sigma_p * z * scale,
        expected_shortfall=sigma_p * es_multiplier * scale,
        observation_count=matrix.observation_count,
        portfolio_volatility_1day=sigma_p,
        z_score=z,
    )
