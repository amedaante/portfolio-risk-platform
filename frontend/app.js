const API_BASE = "http://localhost:8080/api";

function currentPortfolioId() {
  return document.getElementById("portfolioId").value;
}

function fmtMoney(value) {
  const n = Number(value);
  return n.toLocaleString(undefined, { style: "currency", currency: "USD", maximumFractionDigits: 0 });
}

function fmtPct(value) {
  return Number(value).toFixed(2) + "%";
}

async function apiGet(path) {
  const res = await fetch(`${API_BASE}${path}`);
  return handleResponse(res);
}

async function apiPost(path) {
  const res = await fetch(`${API_BASE}${path}`, { method: "POST" });
  return handleResponse(res);
}

async function handleResponse(res) {
  if (!res.ok) {
    let detail = "";
    try {
      const body = await res.json();
      detail = body.message || JSON.stringify(body);
    } catch (_) {
      detail = res.statusText;
    }
    throw new Error(`HTTP ${res.status}: ${detail}`);
  }
  return res.json();
}

function showError(containerId, err) {
  document.getElementById(containerId).innerHTML =
    `<div class="error-box">${err.message}</div>`;
}

// ---------- Portfolio summary + positions ----------

async function loadPortfolio() {
  const pid = currentPortfolioId();
  const summaryEl = document.getElementById("summaryContent");
  const positionsEl = document.getElementById("positionsContent");

  summaryEl.innerHTML = "Loading...";
  positionsEl.innerHTML = "Loading...";

  try {
    const portfolio = await apiGet(`/portfolios/${pid}`);
    summaryEl.innerHTML = `
      <div class="metric-row">
        <div class="metric"><span class="label">Name</span><span class="value">${portfolio.name}</span></div>
        <div class="metric"><span class="label">Base Currency</span><span class="value">${portfolio.baseCurrency}</span></div>
        <div class="metric"><span class="label">Total Market Value</span><span class="value">${fmtMoney(portfolio.totalMarketValue)}</span></div>
      </div>
    `;
  } catch (err) {
    showError("summaryContent", err);
    return;
  }

  try {
    const positions = await apiGet(`/portfolios/${pid}/positions`);
    if (positions.length === 0) {
      positionsEl.innerHTML = `<div class="placeholder">No positions on this portfolio.</div>`;
      return;
    }
    positionsEl.innerHTML = `
      <table>
        <thead><tr><th>Instrument</th><th>Type</th><th>Quantity</th><th>Price</th><th>Market Value</th></tr></thead>
        <tbody>
          ${positions.map(p => `
            <tr>
              <td>${p.instrumentId}</td>
              <td>${p.instrumentType}</td>
              <td>${Number(p.quantity).toLocaleString()}</td>
              <td>${Number(p.price).toFixed(2)}</td>
              <td>${fmtMoney(p.marketValue)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  } catch (err) {
    showError("positionsContent", err);
  }
}

// ---------- VaR ----------

function renderVarResult(result) {
  const container = document.getElementById("varResults");
  const row = `
    <div class="metric-row">
      <div class="metric"><span class="label">${result.methodology}</span><span class="value">${fmtMoney(result.varAmount)}</span></div>
      <div class="metric"><span class="label">Expected Shortfall</span><span class="value">${fmtMoney(result.expectedShortfall)}</span></div>
      <div class="metric"><span class="label">Portfolio Value</span><span class="value">${fmtMoney(result.portfolioValue)}</span></div>
      <div class="metric"><span class="label">Observations</span><span class="value">${result.observationCount}</span></div>
    </div>
    <div class="placeholder">Calculation #${result.id} · as of ${result.marketDataAsOf} · confidence ${(result.confidenceLevel * 100).toFixed(0)}%</div>
  `;
  // Prepend so multiple runs stack, most recent first
  container.innerHTML = container.classList.contains("placeholder-only")
    ? row
    : row + container.innerHTML;
  container.classList.remove("placeholder");
}

async function runVar(methodologyPath) {
  const pid = currentPortfolioId();
  const confidenceLevel = document.getElementById("confidenceLevel").value;
  const horizonDays = document.getElementById("horizonDays").value;

  const container = document.getElementById("varResults");
  container.innerHTML = "Calculating...";

  try {
    const result = await apiPost(
      `/portfolios/${pid}/risk-calculations/${methodologyPath}?confidenceLevel=${confidenceLevel}&horizonDays=${horizonDays}`
    );
    container.innerHTML = "";
    renderVarResult(result);
  } catch (err) {
    showError("varResults", err);
  }
}

// ---------- Risk contribution ----------

async function runRiskContribution() {
  const pid = currentPortfolioId();
  const confidenceLevel = document.getElementById("confidenceLevel").value;
  const horizonDays = document.getElementById("horizonDays").value;
  const container = document.getElementById("contributionResults");

  container.innerHTML = "Calculating...";

  try {
    const result = await apiPost(
      `/portfolios/${pid}/risk-calculations/risk-contribution?confidenceLevel=${confidenceLevel}&horizonDays=${horizonDays}`
    );
    container.innerHTML = `
      <div class="placeholder">Total VaR: ${fmtMoney(result.totalVar)}</div>
      <table>
        <thead><tr><th>Instrument</th><th>Exposure</th><th>Component VaR</th><th>% of Total VaR</th></tr></thead>
        <tbody>
          ${result.contributions.map(c => `
            <tr>
              <td>${c.instrumentId}</td>
              <td>${fmtMoney(c.exposure)}</td>
              <td>${fmtMoney(c.componentVar)}</td>
              <td>${fmtPct(c.pctOfTotalVar)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  } catch (err) {
    showError("contributionResults", err);
  }
}

// ---------- Stress testing ----------

async function runStressTest() {
  const pid = currentPortfolioId();
  const scenarioName = encodeURIComponent(document.getElementById("scenarioName").value);
  const equityShockPct = Number(document.getElementById("equityShockPct").value) / 100;
  const rateShockBps = document.getElementById("rateShockBps").value;
  const fxShockPct = Number(document.getElementById("fxShockPct").value) / 100;

  const container = document.getElementById("stressResults");
  container.innerHTML = "Running scenario...";

  try {
    const result = await apiPost(
      `/portfolios/${pid}/risk-calculations/stress-test?scenarioName=${scenarioName}&equityShockPct=${equityShockPct}&rateShockBps=${rateShockBps}&fxShockPct=${fxShockPct}`
    );

    const pnlClass = result.totalPnl < 0 ? "pnl-negative" : "pnl-positive";

    container.innerHTML = `
      <div class="metric-row">
        <div class="metric"><span class="label">Base Value</span><span class="value">${fmtMoney(result.basePortfolioValue)}</span></div>
        <div class="metric"><span class="label">Stressed Value</span><span class="value">${fmtMoney(result.stressedPortfolioValue)}</span></div>
        <div class="metric"><span class="label">Total P&amp;L</span><span class="value ${pnlClass}">${fmtMoney(result.totalPnl)} (${fmtPct(result.totalPnlPct)})</span></div>
      </div>
      <table>
        <thead><tr><th>Instrument</th><th>Type</th><th>Base Value</th><th>Stressed Value</th><th>P&amp;L</th><th>P&amp;L %</th></tr></thead>
        <tbody>
          ${result.positionImpacts.map(p => `
            <tr>
              <td>${p.instrumentId}</td>
              <td>${p.instrumentType}</td>
              <td>${fmtMoney(p.baseValue)}</td>
              <td>${fmtMoney(p.stressedValue)}</td>
              <td class="${p.pnl < 0 ? 'pnl-negative' : 'pnl-positive'}">${fmtMoney(p.pnl)}</td>
              <td class="${p.pnlPct < 0 ? 'pnl-negative' : 'pnl-positive'}">${fmtPct(p.pnlPct)}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  } catch (err) {
    showError("stressResults", err);
  }
}

// ---------- Risk limits ----------

async function evaluateLimits() {
  const pid = currentPortfolioId();
  const container = document.getElementById("limitsResults");
  container.innerHTML = "Evaluating...";

  try {
    const result = await apiGet(`/portfolios/${pid}/risk-limits/evaluate`);
    if (result.evaluations.length === 0) {
      container.innerHTML = `<div class="placeholder">No limits configured for this portfolio.</div>`;
      return;
    }
    container.innerHTML = `
      <table>
        <thead><tr><th>Limit Type</th><th>Limit</th><th>Current</th><th>Utilization</th><th>Status</th></tr></thead>
        <tbody>
          ${result.evaluations.map(e => `
            <tr>
              <td>${e.limitType}</td>
              <td>${fmtMoney(e.limitValue)}</td>
              <td>${fmtMoney(e.currentValue)}</td>
              <td>${fmtPct(e.utilizationPct)}</td>
              <td><span class="badge ${e.status}">${e.status}</span></td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  } catch (err) {
    showError("limitsResults", err);
  }
}

// ---------- Wire up events ----------

document.getElementById("loadPortfolioBtn").addEventListener("click", loadPortfolio);
document.getElementById("runHistoricalBtn").addEventListener("click", () => runVar("historical-var"));
document.getElementById("runParametricBtn").addEventListener("click", () => runVar("parametric-var"));
document.getElementById("runMonteCarloBtn").addEventListener("click", () => runVar("monte-carlo-var"));
document.getElementById("runContributionBtn").addEventListener("click", runRiskContribution);
document.getElementById("runStressBtn").addEventListener("click", runStressTest);
document.getElementById("evaluateLimitsBtn").addEventListener("click", evaluateLimits);

// Load the default portfolio on page open
loadPortfolio();