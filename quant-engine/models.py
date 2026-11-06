from datetime import date
from typing import Annotated
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Base model that accepts/emits camelCase JSON while keeping
    idiomatic snake_case field names in Python."""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class PricePoint(CamelModel):
    date: date
    price: float = Field(gt=0)


class PositionInput(CamelModel):
    instrument_id: str
    quantity: float
    current_price: float
    price_history: list[PricePoint]


# Bounds reject inputs that previously produced inf/NaN (confidence_level=1
# gives an infinite z-score and a zero ES denominator) or unbounded work
# (num_simulations), which surfaced as HTTP 500s instead of 422s.
ConfidenceLevel = Annotated[float, Field(gt=0.5, lt=1.0)]
HorizonDays = Annotated[int, Field(ge=1, le=250)]


class HistoricalVarRequest(CamelModel):
    confidence_level: ConfidenceLevel = 0.95
    horizon_days: HorizonDays = 1
    positions: list[PositionInput] = Field(min_length=1)


class HistoricalVarResponse(CamelModel):
    methodology: str
    confidence_level: float
    horizon_days: int
    portfolio_value: float
    var: float
    expected_shortfall: float
    observation_count: int
    worst_loss: float
    best_gain: float

class ParametricVarResponse(CamelModel):
    methodology: str
    confidence_level: float
    horizon_days: int
    portfolio_value: float
    var: float
    expected_shortfall: float
    observation_count: int
    portfolio_volatility_1day: float
    z_score: float

class MonteCarloVarRequest(CamelModel):
    confidence_level: ConfidenceLevel = 0.95
    horizon_days: HorizonDays = 1
    num_simulations: int = Field(default=10000, ge=1000, le=1_000_000)
    random_seed: int = 42
    positions: list[PositionInput] = Field(min_length=1)


class MonteCarloVarResponse(CamelModel):
    methodology: str
    confidence_level: float
    horizon_days: int
    portfolio_value: float
    var: float
    expected_shortfall: float
    observation_count: int
    num_simulations: int
    random_seed: int
    worst_loss: float
    best_gain: float

class RiskContributionItem(CamelModel):
    instrument_id: str
    exposure: float
    component_var: float
    pct_of_total_var: float


class RiskContributionResponse(CamelModel):
    confidence_level: float
    horizon_days: int
    portfolio_value: float
    total_var: float
    contributions: list[RiskContributionItem]

class BondParams(CamelModel):
    face_value: float
    coupon_rate: float          # annual, decimal (e.g. 0.05 = 5%)
    coupon_frequency: int       # payments per year (1 = annual, 2 = semiannual)
    years_to_maturity: float
    yield_rate: float           # annual, decimal


class BondAnalyticsResponse(CamelModel):
    price: float
    macaulay_duration: float
    modified_duration: float
    convexity: float
    dv01: float


class RateShockResult(CamelModel):
    shock_bps: int
    shocked_yield: float
    exact_price: float
    exact_price_change: float
    approximated_price_change: float
    approximation_error: float


class RateShockRequest(CamelModel):
    bond: BondParams
    shock_bps: list[int] = [100, 50, -50, -100]


class RateShockResponse(CamelModel):
    base_price: float
    base_yield: float
    modified_duration: float
    convexity: float
    shocks: list[RateShockResult]

class StressPositionInput(CamelModel):
    instrument_id: str
    instrument_type: str          # EQUITY, ETF, BOND, FX
    price_currency: str | None = None  # currency the instrument is quoted in
    quantity: float
    current_price: float
    modified_duration: float | None = None  # BOND only; falls back to scenario default
    convexity: float | None = None          # BOND only; falls back to scenario default


class StressScenarioRequest(CamelModel):
    scenario_name: str
    # When set, current_price values are in this currency and fx_shock_pct
    # is this currency's move against all others. When None, the legacy
    # behaviour applies (every FX position moves by fx_shock_pct).
    base_currency: str | None = None
    equity_shock_pct: float = Field(default=0.0, ge=-1.0)   # e.g. -0.20 for a -20% equity shock
    rate_shock_bps: float = 0.0     # e.g. 100 for a +100bps rate shock
    fx_shock_pct: float = Field(default=0.0, gt=-1.0)       # e.g. 0.05: base currency +5%
    default_bond_duration: float = 5.0
    default_bond_convexity: float = 40.0
    positions: list[StressPositionInput]


class StressPositionImpact(CamelModel):
    instrument_id: str
    instrument_type: str
    base_value: float
    stressed_value: float
    pnl: float
    pnl_pct: float


class StressScenarioResponse(CamelModel):
    scenario_name: str
    equity_shock_pct: float
    rate_shock_bps: float
    fx_shock_pct: float
    base_portfolio_value: float
    stressed_portfolio_value: float
    total_pnl: float
    total_pnl_pct: float
    position_impacts: list[StressPositionImpact]