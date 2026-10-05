from fastapi import FastAPI, HTTPException

from models import HistoricalVarRequest, HistoricalVarResponse, ParametricVarResponse, MonteCarloVarRequest, MonteCarloVarResponse, RiskContributionResponse, BondParams, BondAnalyticsResponse, RateShockRequest, RateShockResponse, StressScenarioRequest, StressScenarioResponse
from risk.historical_simulation import compute_historical_var
from risk.parametric import compute_parametric_var
from risk.monte_carlo import compute_monte_carlo_var
from risk.risk_contribution import compute_risk_contribution
from risk.fixed_income import compute_bond_analytics, compute_rate_shocks
from risk.stress_testing import compute_stress_scenario



app = FastAPI(title="Risk Platform Quant Engine", version="0.1.0")


@app.get("/health")
def health():
    return {"status": "UP"}

@app.post("/risk/historical-var", response_model=HistoricalVarResponse)
def historical_var(request: HistoricalVarRequest):
    try:
        return compute_historical_var(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@app.post("/risk/parametric-var", response_model=ParametricVarResponse)
def parametric_var(request: HistoricalVarRequest):
    try:
        return compute_parametric_var(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@app.post("/risk/monte-carlo-var", response_model=MonteCarloVarResponse)
def monte_carlo_var(request: MonteCarloVarRequest):
    try:
        return compute_monte_carlo_var(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))


@app.post("/risk/risk-contribution", response_model=RiskContributionResponse)
def risk_contribution(request: HistoricalVarRequest):
    try:
        return compute_risk_contribution(request)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@app.post("/fixed-income/analyze", response_model=BondAnalyticsResponse)
def analyze_bond(params: BondParams):
    try:
        return compute_bond_analytics(params)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))


@app.post("/fixed-income/rate-shock", response_model=RateShockResponse)
def rate_shock(request: RateShockRequest):
    try:
        return compute_rate_shocks(request.bond, request.shock_bps)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

@app.post("/risk/stress-test", response_model=StressScenarioResponse)
def stress_test(request: StressScenarioRequest):
    return compute_stress_scenario(request)