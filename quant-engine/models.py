from datetime import date
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Base model that accepts/emits camelCase JSON while keeping
    idiomatic snake_case field names in Python."""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class PricePoint(CamelModel):
    date: date
    price: float


class PositionInput(CamelModel):
    instrument_id: str
    quantity: float
    current_price: float
    price_history: list[PricePoint]


class HistoricalVarRequest(CamelModel):
    confidence_level: float = 0.95
    horizon_days: int = 1
    positions: list[PositionInput]


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
    confidence_level: float = 0.95
    horizon_days: int = 1
    num_simulations: int = 10000
    random_seed: int = 42
    positions: list[PositionInput]


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
    quantity: float
    current_price: float
    modified_duration: float | None = None  # BOND only; falls back to scenario default
    convexity: float | None = None          # BOND only; falls back to scenario default


class StressScenarioRequest(CamelModel):
    scenario_name: str
    equity_shock_pct: float = 0.0   # e.g. -0.20 for a -20% equity shock
    rate_shock_bps: float = 0.0     # e.g. 100 for a +100bps rate shock
    fx_shock_pct: float = 0.0       # e.g. 0.05 for a +5% FX shock
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