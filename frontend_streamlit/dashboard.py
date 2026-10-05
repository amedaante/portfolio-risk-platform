"""
Streamlit dashboard for the Portfolio Risk & Stress Testing Platform.
Talks to the Spring Boot API over HTTP -- same backend as the HTML
dashboard, just a different presentation layer.

Run with: streamlit run dashboard.py
"""

import streamlit as st
import requests
import pandas as pd
import plotly.graph_objects as go

API_BASE = "http://localhost:8080/api"

st.set_page_config(page_title="Portfolio Risk Platform", layout="wide")


def api_get(path, params=None):
    r = requests.get(f"{API_BASE}{path}", params=params)
    r.raise_for_status()
    return r.json()


def api_post(path, params=None):
    r = requests.post(f"{API_BASE}{path}", params=params)
    r.raise_for_status()
    return r.json()


st.title("Portfolio Risk & Stress Testing Platform")

portfolio_id = st.sidebar.number_input("Portfolio ID", min_value=1, value=3, step=1)
confidence_level = st.sidebar.selectbox("Confidence Level", [0.90, 0.95, 0.99], index=1)
horizon_days = st.sidebar.number_input("Horizon (days)", min_value=1, value=1, step=1)

try:
    portfolio = api_get(f"/portfolios/{portfolio_id}")
    positions = api_get(f"/portfolios/{portfolio_id}/positions")
except requests.exceptions.RequestException as e:
    st.error(f"Could not load portfolio {portfolio_id}: {e}")
    st.stop()

# ---------- Summary ----------
col1, col2, col3 = st.columns(3)
col1.metric("Portfolio", portfolio["name"])
col2.metric("Base Currency", portfolio["baseCurrency"])
col3.metric("Total Market Value", f"${portfolio['totalMarketValue']:,.0f}")

st.subheader("Positions")
positions_df = pd.DataFrame(positions)
if not positions_df.empty:
    st.dataframe(
        positions_df[["instrumentId", "instrumentType", "quantity", "price", "marketValue"]],
        use_container_width=True,
    )

    fig_alloc = go.Figure(data=[go.Pie(
        labels=positions_df["instrumentId"],
        values=positions_df["marketValue"],
        hole=0.4,
    )])
    fig_alloc.update_layout(title="Portfolio Allocation", height=350)
    st.plotly_chart(fig_alloc, use_container_width=True)

# ---------- VaR comparison ----------
st.subheader("VaR Methodology Comparison")

run_var = st.button("Run All Three VaR Methodologies")

if run_var:
    with st.spinner("Calculating..."):
        methodologies = {
            "Historical": "historical-var",
            "Parametric": "parametric-var",
            "Monte Carlo": "monte-carlo-var",
        }
        results = {}
        for label, path in methodologies.items():
            try:
                results[label] = api_post(
                    f"/portfolios/{portfolio_id}/risk-calculations/{path}",
                    params={"confidenceLevel": confidence_level, "horizonDays": horizon_days},
                )
            except requests.exceptions.RequestException as e:
                st.error(f"{label} VaR failed: {e}")

        if results:
            comparison_df = pd.DataFrame([
                {"Methodology": label, "VaR": r["varAmount"], "Expected Shortfall": r["expectedShortfall"]}
                for label, r in results.items()
            ])

            fig = go.Figure()
            fig.add_bar(name="VaR", x=comparison_df["Methodology"], y=comparison_df["VaR"])
            fig.add_bar(name="Expected Shortfall", x=comparison_df["Methodology"], y=comparison_df["Expected Shortfall"])
            fig.update_layout(barmode="group", title="VaR vs Expected Shortfall by Methodology", height=400)
            st.plotly_chart(fig, use_container_width=True)

            st.dataframe(comparison_df.style.format({"VaR": "${:,.0f}", "Expected Shortfall": "${:,.0f}"}),
                         use_container_width=True)

# ---------- Risk contribution ----------
st.subheader("Risk Contribution")

if st.button("Calculate Risk Contribution"):
    with st.spinner("Calculating..."):
        try:
            contrib = api_post(
                f"/portfolios/{portfolio_id}/risk-calculations/risk-contribution",
                params={"confidenceLevel": confidence_level, "horizonDays": horizon_days},
            )
            contrib_df = pd.DataFrame(contrib["contributions"])

            fig = go.Figure(data=[go.Bar(
                x=contrib_df["instrumentId"],
                y=contrib_df["pctOfTotalVar"],
                marker_color=["#ef4f64" if v > 25 else "#4f8cff" for v in contrib_df["pctOfTotalVar"]],
            )])
            fig.update_layout(title=f"% of Total VaR by Instrument (Total VaR: ${contrib['totalVar']:,.0f})",
                               yaxis_title="% of Total VaR", height=400)
            st.plotly_chart(fig, use_container_width=True)
            st.dataframe(contrib_df, use_container_width=True)
        except requests.exceptions.RequestException as e:
            st.error(f"Risk contribution failed: {e}")

# ---------- Stress testing ----------
st.subheader("Stress Testing")

sc1, sc2, sc3, sc4 = st.columns(4)
scenario_name = sc1.text_input("Scenario Name", "2008-style crash")
equity_shock = sc2.number_input("Equity Shock %", value=-20.0, step=1.0)
rate_shock = sc3.number_input("Rate Shock (bps)", value=100, step=10)
fx_shock = sc4.number_input("FX Shock %", value=5.0, step=1.0)

if st.button("Run Stress Test"):
    with st.spinner("Running scenario..."):
        try:
            stress = api_post(
                f"/portfolios/{portfolio_id}/risk-calculations/stress-test",
                params={
                    "scenarioName": scenario_name,
                    "equityShockPct": equity_shock / 100,
                    "rateShockBps": rate_shock,
                    "fxShockPct": fx_shock / 100,
                },
            )

            m1, m2, m3 = st.columns(3)
            m1.metric("Base Value", f"${stress['basePortfolioValue']:,.0f}")
            m2.metric("Stressed Value", f"${stress['stressedPortfolioValue']:,.0f}")
            m3.metric("Total P&L", f"${stress['totalPnl']:,.0f}", f"{stress['totalPnlPct']:.2f}%")

            impacts_df = pd.DataFrame(stress["positionImpacts"])
            fig = go.Figure(data=[go.Bar(
                x=impacts_df["instrumentId"],
                y=impacts_df["pnl"],
                marker_color=["#ef4f64" if v < 0 else "#3ecf8e" for v in impacts_df["pnl"]],
            )])
            fig.update_layout(title=f'P&L by Position — "{scenario_name}"', yaxis_title="P&L ($)", height=400)
            st.plotly_chart(fig, use_container_width=True)
            st.dataframe(impacts_df, use_container_width=True)
        except requests.exceptions.RequestException as e:
            st.error(f"Stress test failed: {e}")

# ---------- Risk limits ----------
st.subheader("Risk Limits")

if st.button("Evaluate Limits"):
    try:
        limits = api_get(f"/portfolios/{portfolio_id}/risk-limits/evaluate")
        if not limits["evaluations"]:
            st.info("No limits configured for this portfolio.")
        else:
            limits_df = pd.DataFrame(limits["evaluations"])

            def status_color(status):
                return {"WITHIN": "🟢", "WARNING": "🟡", "BREACHED": "🔴"}.get(status, "")

            limits_df["status"] = limits_df["status"].apply(lambda s: f"{status_color(s)} {s}")
            st.dataframe(limits_df, use_container_width=True)
    except requests.exceptions.RequestException as e:
        st.error(f"Limit evaluation failed: {e}")