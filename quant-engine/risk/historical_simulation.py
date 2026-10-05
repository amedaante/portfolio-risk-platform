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
import pandas as pd

from models import HistoricalVarRequest, HistoricalVarResponse


def compute_historical_var(request: HistoricalVarRequest) -> HistoricalVarResponse:
    position_frames = []
    current_values = {}

    for position in request.positions:
        df = pd.DataFrame(
            [(p.date, p.price) for p in position.price_history],
            columns=["date", "price"],
        ).set_index("date").sort_index()

        returns = df["price"].pct_change().dropna()
        returns.name = position.instrument_id
        position_frames.append(returns)

        current_values[position.instrument_id] = position.quantity * position.current_price

    # Align all instruments on common dates (inner join) — a date only
    # counts as a scenario if every instrument has a return for it.
    returns_df = pd.concat(position_frames, axis=1, join="inner")

    if returns_df.empty or len(returns_df) < 2:
        raise ValueError(
            "Not enough overlapping price history across positions to compute historical VaR"
        )

    current_value_vector = pd.Series(current_values).reindex(returns_df.columns)
    portfolio_value = float(current_value_vector.sum())

    # Simulated portfolio P&L for each historical day
    pnl = returns_df.dot(current_value_vector)

    confidence = request.confidence_level
    tail_quantile = 1 - confidence

    loss_threshold = float(np.percentile(pnl, tail_quantile * 100))
    var_1day = max(0.0, -loss_threshold)

    tail_losses = pnl[pnl <= loss_threshold]
    es_1day = float(-tail_losses.mean()) if len(tail_losses) > 0 else var_1day

    # Square-root-of-time scaling to the requested horizon.
    # Simplifying assumption: assumes i.i.d. returns — flagged as a
    # known limitation in risk-methodology.md, not presented as exact.
    scale = np.sqrt(request.horizon_days)
    var_scaled = var_1day * scale
    es_scaled = es_1day * scale

    return HistoricalVarResponse(
        methodology="HISTORICAL_SIMULATION",
        confidence_level=confidence,
        horizon_days=request.horizon_days,
        portfolio_value=portfolio_value,
        var=var_scaled,
        expected_shortfall=es_scaled,
        observation_count=len(pnl),
        worst_loss=float(-pnl.min()),
        best_gain=float(pnl.max()),
    )