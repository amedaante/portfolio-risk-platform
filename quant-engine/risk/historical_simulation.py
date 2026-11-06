"""
Historical simulation VaR and Expected Shortfall.

Method: for each historical day, apply that day's observed instrument
returns to TODAY's position sizes to build a simulated portfolio P&L
distribution. VaR is the loss at the given confidence quantile;
Expected Shortfall is the average loss in the tail beyond VaR.

This is the standard non-parametric historical VaR approach — it makes
no distributional assumption about returns, which is both its main
strength (captures real fat tails/skew) and its main weakness (fully
dependent on the lookback window actually containing representative
stress events).
"""

import numpy as np

from models import HistoricalVarRequest, HistoricalVarResponse
from risk.returns import build_returns_matrix


def compute_historical_var(request: HistoricalVarRequest) -> HistoricalVarResponse:
    matrix = build_returns_matrix(request.positions, "historical VaR")

    # Simulated portfolio P&L for each historical day
    pnl = matrix.returns.to_numpy() @ matrix.exposures

    confidence = request.confidence_level
    tail_quantile = 1 - confidence

    loss_threshold = float(np.percentile(pnl, tail_quantile * 100))
    var_1day = max(0.0, -loss_threshold)

    tail_losses = pnl[pnl <= loss_threshold]
    es_1day = float(-tail_losses.mean()) if len(tail_losses) > 0 else var_1day

    # Square-root-of-time scaling to the requested horizon.
    # Simplifying assumption: assumes i.i.d. returns — flagged as a
    # known limitation in Risk-Methodology.md, not presented as exact.
    scale = np.sqrt(request.horizon_days)

    return HistoricalVarResponse(
        methodology="HISTORICAL_SIMULATION",
        confidence_level=confidence,
        horizon_days=request.horizon_days,
        portfolio_value=matrix.portfolio_value,
        var=var_1day * scale,
        expected_shortfall=es_1day * scale,
        observation_count=matrix.observation_count,
        worst_loss=float(-pnl.min()),
        best_gain=float(pnl.max()),
    )
