CREATE TABLE risk_limit (
    id                      BIGSERIAL PRIMARY KEY,
    portfolio_id            BIGINT        NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    limit_type              VARCHAR(30)   NOT NULL, -- VAR, EXPECTED_SHORTFALL, EQUITY_EXPOSURE_PCT, FX_EXPOSURE
    limit_value             NUMERIC(24,6) NOT NULL,
    warning_threshold_pct   NUMERIC(5,2)  NOT NULL DEFAULT 80.00,
    created_at              TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_limit_portfolio ON risk_limit(portfolio_id);