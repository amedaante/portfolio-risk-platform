"""
Monte Carlo VaR via a one-step multivariate normal simulation of daily
instrument returns.

Method:
1. Estimate the daily covariance matrix of instrument returns (same
   inputs as parametric VaR).
2. Decompose it as Sigma = L L^T (Cholesky). Multiplying a vector of
   independent standard normal draws z by L gives draws with covariance
   L I L^T = Sigma -- this is how correlation between instruments
   (e.g. AAPL and SPY moving together) gets baked into the simulation
   instead of treating each instrument independently.
3. Simulate N one-day return vectors, apply them to current exposures to
   get N simulated portfolio P&L outcomes.
4. VaR/ES come from the percentile of that simulated distribution,
   same as historical simulation -- just with a much larger, synthetic
   sample. Scale to the horizon with sqrt(h), as the other methods do.

This is not geometric Brownian motion: there is no lognormal price path,
only one draw of simple returns per scenario. That is equivalent for a
one-day horizon and linear positions.

Zero mean, like parametric VaR. The sample mean of ~250 daily returns is
statistically indistinguishable from zero, and the previous version added
it and then scaled it by sqrt(h) (drift actually scales with h). Using
zero keeps Monte Carlo and parametric estimating the same quantity.

Known limitation (Risk-Methodology.md): still assumes returns are drawn
from a multivariate normal, so it does NOT capture fat tails. A Student-t
or bootstrapped-historical sampler is the documented next step.
"""

import numpy as np

from models import MonteCarloVarRequest, MonteCarloVarResponse
from risk.returns import build_returns_matrix


def correlating_matrix(cov_matrix: np.ndarray) -> np.ndarray:
    """
    Returns L with L @ L.T ~= cov_matrix, used to correlate standard normals.

    Cholesky needs a positive-definite matrix. A sample covariance can be
    only positive semi-definite (more instruments than observations, or
    perfectly collinear instruments), and float error can push an
    eigenvalue slightly negative. Try Cholesky with a tiny diagonal
    jitter first; if it still fails, fall back to an eigendecomposition
    with negative eigenvalues clipped to zero (the nearest PSD matrix in
    that basis), instead of crashing with LinAlgError -> HTTP 500.
    """
    n = cov_matrix.shape[0]
    try:
        return np.linalg.cholesky(cov_matrix + 1e-10 * np.eye(n))
    except np.linalg.LinAlgError:
        eigenvalues, eigenvectors = np.linalg.eigh(cov_matrix)
        return eigenvectors * np.sqrt(np.clip(eigenvalues, 0.0, None))


def compute_monte_carlo_var(request: MonteCarloVarRequest) -> MonteCarloVarResponse:
    matrix = build_returns_matrix(request.positions, "Monte Carlo VaR")

    cov_matrix = matrix.returns.cov().to_numpy()
    chol = correlating_matrix(cov_matrix)

    rng = np.random.default_rng(request.random_seed)
    n_sims = request.num_simulations

    # Independent standard normal draws, shape (n_sims, n_instruments),
    # correlated via L. Rows are scenarios, so it's z @ L^T rather than L @ z.
    z = rng.standard_normal((n_sims, len(matrix.instruments)))
    simulated_returns = z @ chol.T

    # Simulated portfolio P&L for each of the n_sims scenarios
    pnl = simulated_returns @ matrix.exposures

    confidence = request.confidence_level
    tail_quantile = 1 - confidence

    loss_threshold = float(np.percentile(pnl, tail_quantile * 100))
    var_1day = max(0.0, -loss_threshold)

    tail_losses = pnl[pnl <= loss_threshold]
    es_1day = float(-tail_losses.mean()) if len(tail_losses) > 0 else var_1day

    scale = np.sqrt(request.horizon_days)

    return MonteCarloVarResponse(
        methodology="MONTE_CARLO",
        confidence_level=confidence,
        horizon_days=request.horizon_days,
        portfolio_value=matrix.portfolio_value,
        var=var_1day * scale,
        expected_shortfall=es_1day * scale,
        observation_count=matrix.observation_count,
        num_simulations=n_sims,
        random_seed=request.random_seed,
        worst_loss=float(-pnl.min()),
        best_gain=float(pnl.max()),
    )
