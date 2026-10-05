# Risk Methodology

This document explains the quantitative methods implemented in this platform: what each one computes, the assumptions it makes, and — deliberately — where it breaks down. Nothing here is presented as institutional or regulatory-grade; the goal is to demonstrate correct implementation of standard techniques, not to build a production risk model.

## Value at Risk (VaR) — three methodologies

VaR answers: "What is the maximum loss I expect, with X% confidence, over a given holding period?" All three implementations here answer that question differently, and the differences are the point — a single VaR number without knowing its methodology's assumptions is close to meaningless in practice.

### 1. Historical Simulation VaR

**Method:** Take the portfolio's current position sizes. Apply each of the last 252 days' *actual observed* instrument returns to those position sizes, generating 251 simulated portfolio P&L outcomes (one per day, after dropping the first day to compute a return). VaR is the loss at the requested confidence percentile of that distribution; Expected Shortfall is the average loss beyond that percentile.

**Strength:** Makes no assumption about the shape of the return distribution — if real returns are fat-tailed, skewed, or exhibit volatility clustering, historical simulation captures that automatically, because it's using real historical co-movements.

**Weakness:** Entirely dependent on the lookback window containing representative scenarios. A calm 252-day window will underestimate risk; a window containing a crash will overestimate ongoing risk. With only ~251 observations, the tail estimate (especially at 99% confidence, where you're estimating from the single worst ~2-3 observations) is statistically noisy.

**Implementation:** `quant-engine/risk/historical_simulation.py`. Horizon scaling beyond 1 day uses the square-root-of-time rule, which assumes i.i.d. returns — a simplification documented here and worth challenging in an interview context.

### 2. Parametric (Variance-Covariance) VaR

**Method:** Assumes portfolio returns are normally distributed. Computes portfolio volatility analytically from the covariance matrix of instrument returns and current dollar exposures: `σ_p = sqrt(eᵀ Σ e)`, where `e` is the exposure vector and `Σ` is the covariance matrix. VaR follows directly from the normal distribution's z-score at the target confidence level; Expected Shortfall uses the closed-form normal-tail formula `ES = σ × φ(z) / (1 − confidence)`.

**Strength:** Fast (no simulation), fully analytical, and the building block for risk contribution (below).

**Weakness:** Normality is a real assumption, not a technicality — equity and FX returns are empirically fat-tailed and often negatively skewed. Parametric VaR systematically understates true tail risk. This is visible directly in this project: Parametric VaR on the demo portfolio (TSLA-heavy, 55% annualized vol in the synthetic data) comes in meaningfully lower than Historical VaR on the same book — a concrete, demonstrable instance of the limitation, not just a textbook caveat.

**Implementation:** `quant-engine/risk/parametric.py`.

### 3. Monte Carlo VaR

**Method:** Estimates the same covariance matrix as Parametric VaR, then Cholesky-decomposes it (`Σ = LLᵀ`) to generate *correlated* random return scenarios: independent standard normal draws are multiplied by `L` to produce draws matching the real covariance structure, then the mean daily return is added back. Each of `N` simulated days produces a portfolio P&L outcome; VaR/ES come from the percentile of that large synthetic distribution — same quantile mechanics as Historical VaR, but on a sample size you control rather than one fixed by available history.

**Why Cholesky matters:** Without it, simulating each instrument's returns independently would ignore correlation entirely — e.g., AAPL and SPY tend to move together, and a simulation that doesn't encode that would produce an unrealistically wide (or narrow, depending on the instrument mix) P&L distribution. Cholesky decomposition is the standard mechanism for injecting a target correlation structure into independent random draws.

**Strength:** Captures correlation properly while allowing arbitrarily large sample sizes — the tail estimate isn't limited to however many real historical days you have.

**Weakness:** As implemented, still draws from a multivariate *normal* distribution, so it inherits Parametric VaR's normality assumption — it improves on correlation handling and sample size, not on tail shape. A more advanced version would simulate from a fitted Student-t distribution or bootstrap from historical returns instead of drawing normals. This is a known, documented extension point, not an oversight.

**Reproducibility:** Given a fixed `randomSeed`, results are bit-for-bit identical across runs — verified directly by `test_same_seed_produces_identical_results` in the test suite. This is a deliberate design choice for auditability: a stored risk calculation's `requestParams` (including the seed) is enough to reproduce the exact result later.

**Implementation:** `quant-engine/risk/monte_carlo.py`.

### Methodology comparison (on the demo portfolio)

| | Historical | Parametric | Monte Carlo |
|---|---|---|---|
| Distribution assumption | None (empirical) | Normal | Normal (via simulation) |
| Captures correlation | Yes (implicitly, via real co-movement) | Yes (via covariance matrix) | Yes (via Cholesky decomposition) |
| Captures fat tails | Yes | No | No |
| Sample size | Fixed by history (251 obs here) | N/A (closed-form) | Arbitrary (configurable) |
| Speed | Fast | Fastest | Slower (scales with simulation count) |
| Reproducible given same inputs | Yes (deterministic) | Yes (deterministic) | Yes (given fixed seed) |

## Expected Shortfall (Conditional VaR)

Expected Shortfall answers a different question than VaR: not "what's the loss at the Xth percentile," but "given that we're in the tail beyond VaR, what's the *average* loss?" ES is a coherent risk measure (it satisfies subadditivity — the ES of a combined portfolio is never worse than the sum of its parts' ES) in a way VaR is not; this is the standard theoretical reason modern regulatory frameworks (e.g., FRTB) have shifted toward ES. In this implementation, ES ≥ VaR is enforced as a structural property and tested directly (`test_expected_shortfall_never_less_than_var`).

## Risk Contribution (Component VaR)

**Method:** Decomposes total parametric VaR into additive per-instrument contributions, using Euler's homogeneity theorem. Because portfolio volatility `σ_p = sqrt(eᵀ Σ e)` is linear-homogeneous of degree 1 in the exposure vector, the per-instrument component `C_i = e_i · (Σe)_i / σ_p` has the property that `Σ C_i = σ_p` *exactly* — not approximately. Scaling each component by the same `z × √horizon` factor used for total VaR means each instrument's component VaR sums exactly to total VaR.

**Why this matters:** A risk desk doesn't just want to know total VaR — it wants to know which positions are *driving* that risk, so hedging or de-risking decisions can target the right exposures. The additivity property is what makes the breakdown meaningful rather than an arbitrary heuristic; it's asserted directly in `test_component_var_sums_to_total_var`.

**Implementation:** `quant-engine/risk/risk_contribution.py`.

## Stress Testing

**Method:** Applies configurable shocks by instrument type — direct percentage shocks for equity/ETF and FX positions, duration+convexity-based repricing for bonds — and reports base-vs-stressed portfolio value, both at the portfolio level and per-position. Position impacts are sorted largest-magnitude-first.

**Distinction from VaR:** VaR answers "what's a plausible loss under normal market conditions." Stress testing answers a different question: "what happens under a *specific, named, potentially extreme* scenario I construct myself" (e.g., "equities down 20%, rates up 100bps, USD up 5%" — a stylized 2008-like scenario). It doesn't rely on a probability distribution at all; the scenario is a direct input, which is exactly why it's a necessary complement to VaR rather than a redundant check — VaR can systematically miss scenarios outside its historical/distributional assumptions, which stress testing is designed to probe directly.

**Implementation:** `quant-engine/risk/stress_testing.py`.

## Fixed-Income Analytics

**Method:** Standard closed-form bond mathematics — discounted cashflow pricing, Macaulay duration (PV-weighted average time to cashflow), modified duration (first-order price sensitivity to yield), convexity (second-order correction), and DV01 (dollar price change per 1bp yield move).

**Why both duration and convexity:** Duration alone is a linear (first-order) approximation of a genuinely curved price-yield relationship. For small yield moves, duration alone is accurate; for larger moves, the approximation error grows measurably — this project demonstrates that directly: `test_rate_shock_approximation_error_grows_with_shock_size` confirms the duration-only approximation's error increases with shock magnitude, which is exactly the textbook justification for including a convexity correction term.

**Validation:** All formulas are tested against a hand-computed 2-year, 5% annual-coupon par bond (price must equal exactly 100 when coupon = yield — a strong, independently verifiable check) rather than against the implementation's own output.

**Implementation:** `quant-engine/risk/fixed_income.py`.