-- A VAR / EXPECTED_SHORTFALL limit was compared against whichever
-- calculation ran last, mixing methodologies, confidence levels and
-- horizons. These optional columns pin a limit to a specific kind of
-- calculation; NULL means "any".
ALTER TABLE risk_limit
    ADD COLUMN methodology      VARCHAR(50),
    ADD COLUMN confidence_level NUMERIC(5,4),
    ADD COLUMN horizon_days     INT;

-- Supports "latest calculation for this portfolio" lookups during limit
-- evaluation (previously a full-table findAll() filtered in memory).
CREATE INDEX idx_risk_calc_portfolio_ts ON risk_calculation(portfolio_id, calculation_timestamp DESC);
