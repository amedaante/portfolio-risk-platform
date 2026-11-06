"""
Shared input preparation for every covariance/returns-based calculation.

This block was previously copy-pasted into historical, parametric,
Monte Carlo and risk-contribution modules.
"""

from dataclasses import dataclass

import numpy as np
import pandas as pd

from models import PositionInput


@dataclass(frozen=True)
class ReturnsMatrix:
    returns: pd.DataFrame        # rows = dates, columns = instrument ids (simple daily returns)
    exposures: np.ndarray        # current value per instrument, same order as returns.columns
    instruments: list[str]

    @property
    def portfolio_value(self) -> float:
        return float(self.exposures.sum())

    @property
    def observation_count(self) -> int:
        return len(self.returns)


def build_returns_matrix(positions: list[PositionInput], method_name: str) -> ReturnsMatrix:
    """
    Daily simple returns per instrument, aligned on common dates (inner
    join: a date counts only if every instrument has a return for it),
    plus current exposures (quantity x current price) in matching order.

    Two positions in the same instrument are combined into one exposure,
    so the returns matrix never contains duplicate columns.
    """
    position_frames = {}
    exposures: dict[str, float] = {}

    for position in positions:
        exposures[position.instrument_id] = (
            exposures.get(position.instrument_id, 0.0) + position.quantity * position.current_price
        )
        if position.instrument_id in position_frames:
            continue

        df = pd.DataFrame(
            [(p.date, p.price) for p in position.price_history],
            columns=["date", "price"],
        ).set_index("date").sort_index()

        returns = df["price"].pct_change().dropna()
        returns.name = position.instrument_id
        position_frames[position.instrument_id] = returns

    returns_df = pd.concat(list(position_frames.values()), axis=1, join="inner")
    if returns_df.empty or len(returns_df) < 2:
        raise ValueError(
            f"Not enough overlapping price history across positions to compute {method_name}"
        )

    instruments = list(returns_df.columns)
    exposure_vector = pd.Series(exposures).reindex(instruments).to_numpy(dtype=float)
    return ReturnsMatrix(returns=returns_df, exposures=exposure_vector, instruments=instruments)


def portfolio_stddev(cov_matrix: np.ndarray, exposures: np.ndarray) -> float:
    """sigma_p = sqrt(e^T Sigma e), in currency units. Clamped at 0 for float noise."""
    variance = float(exposures @ cov_matrix @ exposures)
    return float(np.sqrt(max(variance, 0.0)))
