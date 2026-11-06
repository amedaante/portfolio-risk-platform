# Design Decisions and Bug Log

Why the system is shaped the way it is, and the defects found in review: what each one was, how it was proven, and how it was fixed. Every fix has a test that fails on the old code.

## Decisions

**Two services.** Spring Boot owns the data (portfolios, positions, market data, limits, the calculation audit trail). A stateless FastAPI engine does the maths. The engine never touches the database, so it can be tested as pure functions and scaled horizontally. The cost is a network hop and a JSON contract that both sides must keep in sync.

**Short transactions around a network call.** A risk calculation is: load inputs (read-only transaction), call the engine (no transaction), save the result (write transaction). Holding a pooled connection for up to the 60-second read timeout would starve other requests, and a database rollback can't undo a remote call anyway.

**HTTP/1.1 to the engine.** The JDK `HttpClient` defaults to HTTP/2. On a plain `http://` URL it sends an HTTP/1.1 request carrying `Upgrade: h2c` headers. Uvicorn does not implement h2c, and the upgraded POST was not handled correctly, so the engine never received a usable body. `QuantEngineConfig` pins HTTP/1.1. `QuantEngineClientTest` asserts the exact outgoing JSON, including ISO dates.

**Flyway owns the schema.** `ddl-auto: validate` fails startup if entities and tables drift apart.

**Base-currency valuation.** Every value and risk number is in the portfolio's base currency. See Risk-Methodology.md, Currency Handling.

## Bug log

### 1. `@Transactional` annotations that did nothing

- **Symptom:** none visible. The README claimed no connection was held during the engine call, and that was false.
- **Cause:** `RiskCalculationService` called its own `@Transactional` methods through `this`. Spring applies transactions through a proxy, and self-invocation bypasses the proxy. Separately, Spring Boot's default `open-in-view=true` keeps the Hibernate session, and its JDBC connection, for the whole HTTP request.
- **Fix:** the database work moved to a separate bean, `RiskCalculationDataService` (`loadInput`, `saveCalculation`). `spring.jpa.open-in-view: false`.
- **Proof:** `RiskCalculationTransactionBoundaryTest` runs the real proxies against a recording transaction manager. It asserts a read-only transaction during the load, none during the engine call, and a read-write transaction during the save. `selfInvocationBypassesTheTransactionalProxy` reproduces the original mistake in isolation.

### 2. The audit trail was always empty

- **Symptom:** `risk_calculation.request_params` was NULL on every row.
- **Cause:** the service built its own `new ObjectMapper()`, which has no `JavaTimeModule`. Jackson 2.12+ refuses to serialise `LocalDate`, and the exception was caught and discarded.
- **Fix:** inject Spring Boot's configured `ObjectMapper`. Log serialisation failures.
- **Proof:** `auditTrailStoresTheFullRequestIncludingDates` deserialises the stored payload and checks a date. `rootCauseOfTheOldAuditBug_plainObjectMapperCannotWriteLocalDate` shows the root cause.

### 3. Currencies added together without conversion

- **Symptom:** the demo's 50,000 USDINR position was valued at $3.67M. That is 3.67M *INR*, which is $50,000. It made up 77% of portfolio value and 83% of VaR. In the "equities −20%, rates +100bp, USD +5%" stress scenario, it added a fictitious +$183k and turned a −15.8% loss into a reported +0.12% gain.
- **Cause:** `quantity × price` was summed across positions priced in different currencies; the `currency` column was never read.
- **Fix:** `Position.priceCurrency()` (FX pairs are priced in their quote currency), `FxConverter` and `FxRateService` (rates from FX pair prices, direct or inverted), and base-currency valuation in risk inputs, portfolio totals and limit exposures.
- **Proof:** `FxConverterTest` (`inversePairDivides_usdInrPositionIsWorthItsUsdNotional`).

### 4. FX stress shock moved every pair the same way

- **Symptom:** "USD +5%" made both USDINR and EURUSD gain, though a long EURUSD position loses when USD strengthens.
- **Fix:** `fxShockPct` is now the base currency's move against all others, applied per pair direction, with translation for other foreign-priced assets. Without a base currency, the old rule still applies, for backward compatibility.
- **Proof:** five tests in `test_stress_testing.py` (`test_usd_strength_hurts_long_eurusd`, ...).

### 5. Two lots of the same instrument were mis-valued

- **Symptom:** AAPL ×100 plus AAPL ×300 produced 1.5× the VaR of a single AAPL ×400 position.
- **Cause:** the exposures dict kept only the last lot (300), and the duplicated return column then counted it twice, so the model held 600 shares.
- **Fix:** `risk/returns.py` merges lots per instrument. It also replaces the block that had been copy-pasted into four modules.
- **Proof:** `test_two_lots_of_the_same_instrument_equal_one_combined_position`.

### 6. Out-of-range inputs became server errors

- **Symptom:** `confidenceLevel=1.0` produced VaR = `inf` and ES = `NaN`. The engine failed to serialise them (500), and the backend reported "service temporarily unavailable" (502).
- **Fix:** bounds on both sides. Spring 6.1 method validation on `@RequestParam` gives a 400; Pydantic `Field` constraints give a 422 from the engine.
- **Proof:** `confidenceLevelOfOneIsRejectedBeforeReachingTheEngine`, `test_confidence_level_outside_half_to_one_is_rejected`.

### 7. Misleading error statuses

- **Symptom:** "Portfolio has no positions", "calculation not found" and a bad enum value all returned 500. Every engine 422 ("not enough overlapping history") returned 502, "temporarily unavailable".
- **Fix:** `ApiException` carries its status (400/404/409/422). The engine's 4xx responses map to 422; only unreachable engines and engine 5xx responses map to 502.
- **Proof:** `RiskCalculationControllerErrorMappingTest`.

### 8. Monte Carlo drift, and crashes on singular covariance

- **Drift:** the sample mean was added and then scaled by √h, though drift scales with h. On a drifting series, Monte Carlo VaR came out 53% below parametric. It is now zero-mean, like parametric.
- **Singular covariance:** Cholesky raised `LinAlgError` (500) for collinear instruments. It now falls back to an eigendecomposition with clipped eigenvalues.
- **Proof:** `test_monte_carlo_is_zero_mean_like_parametric`, `test_correlating_matrix_falls_back_instead_of_crashing_on_indefinite_input`, `test_monte_carlo_handles_perfectly_collinear_instruments`.

### 9. Risk limits compared against the wrong calculation

- **Symptom:** a VaR limit was checked against whatever calculation ran last: a 10-day 99% Monte Carlo result one minute, a 1-day 95% historical result the next. The lookup was `findAll()` on the whole table, filtered in memory. With no calculation on record, the whole evaluation failed with a 500.
- **Fix:** V4 migration adds optional `methodology`, `confidence_level` and `horizon_days` to `risk_limit`, plus an index on `risk_calculation(portfolio_id, calculation_timestamp DESC)`. The lookup is a JPA Specification with `LIMIT 1`. A limit with no matching calculation reports `NO_DATA`.

### 10. Smaller fixes

- **N+1 queries:** risk inputs ran one price query per position; it is now one `IN` query. The portfolio list lazy-loaded positions per portfolio; it now uses `@EntityGraph`.
- **CSV upload:** a SELECT plus an INSERT per row, with no batching (IDENTITY ids), racing on concurrent uploads, and an `ArrayIndexOutOfBounds` 500 on malformed lines. It is now a batched Postgres `INSERT ... ON CONFLICT DO UPDATE` with line-numbered 400 errors.
- **Deletes not scoped to their portfolio:** `DELETE /risk-limits/{id}` and `DELETE /positions/{id}` ignored which portfolio they belonged to.
- **Deleting a portfolio that has calculations** failed on the audit FK with a 500. It is now a clear 409 that preserves the audit trail.
- **Market data endpoint** returned JPA entities; it now returns a DTO.
- **Duplicated client code:** five identical `RestClient` methods now share one `post` helper.
- **Stray files:** removed empty `var_historical.py` and `expected_shortfall.py`; moved demo scripts out of `src/main/resources` (they were packaged into the JAR); moved an unrelated C++ file out of the Java source tree.

## Still open

- Stress-test and risk-contribution results are not persisted to the audit trail.
- Calculations are synchronous. At scale they should be asynchronous jobs (PENDING → COMPLETED) on a queue.
- No authentication.
- No Postgres integration tests yet (Testcontainers). The V4 migration and the upsert SQL are verified by compilation and review, not by a running database.
