"""
Monte Carlo VaR via correlated geometric Brownian motion simulation.

Method:
1. Estimate the daily covariance matrix of instrument returns (same
   inputs as parametric VaR).
2. Cholesky-decompose it: Sigma = L L^T. Multiplying a vector of
   independent standard normal draws by L produces draws with the
   correct covariance structure — this is how correlation between
   instruments (e.g. AAPL and SPY moving together) gets baked into
   the simulation instead of treating each instrument independently.
3. Simulate N days of correlated returns, apply them to current
   dollar exposures to get N simulated portfolio P&L outcomes.
4. VaR/ES come from the percentile of that simulated distribution,
   same as historical simulation — just with a much larger,
   synthetic sample.

Known limitation (for risk-methodology.md): still assumes returns are
drawn from a multivariate normal — it improves on Parametric VaR by
capturing correlation and by not needing a closed-form tail formula,
but it does NOT fix the fat-tail/non-normality limitation on its own.
A more advanced version would sample from a fitted Student-t or use
bootstrapped historical returns instead of normal draws — documented
as a future enhancement, not implemented here.
"""

import numpy as np
import pandas as pd

from models import MonteCarloVarRequest, MonteCarloVarResponse


def compute_monte_carlo_var(request: MonteCarloVarRequest) -> MonteCarloVarResponse:
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
            "Not enough overlapping price history across positions to compute Monte Carlo VaR"
        )

    instruments = list(returns_df.columns)
    mean_returns = returns_df.mean().to_numpy()
    cov_matrix = returns_df.cov().to_numpy()
    exposure_vector = pd.Series(exposures).reindex(instruments).to_numpy()
    portfolio_value = float(exposure_vector.sum())

    # Cholesky decomposition: Sigma = L L^T.
    # Add a tiny diagonal jitter in case the covariance matrix is only
    # positive semi-definite (common with few overlapping observations
    # or highly correlated instruments) rather than strictly positive
    # definite, which Cholesky requires.
    jitter = 1e-10 * np.eye(len(instruments))
    chol = np.linalg.cholesky(cov_matrix + jitter)

    rng = np.random.default_rng(request.random_seed)
    n_sims = request.num_simulations
    n_instruments = len(instruments)

    # Independent standard normal draws, shape (n_sims, n_instruments)
    z = rng.standard_normal((n_sims, n_instruments))

    # Correlate them via Cholesky, then add back the mean daily return
    simulated_returns = mean_returns + z @ chol.T

    # Simulated portfolio P&L for each of the n_sims scenarios
    pnl = simulated_returns @ exposure_vector

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
        portfolio_value=portfolio_value,
        var=var_1day * scale,
        expected_shortfall=es_1day * scale,
        observation_count=len(returns_df),
        num_simulations=n_sims,
        random_seed=request.random_seed,
        worst_loss=float(-pnl.min()),
        best_gain=float(pnl.max()),
    )