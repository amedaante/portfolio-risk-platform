CREATE TABLE risk_calculation (
    id                      BIGSERIAL PRIMARY KEY,
    portfolio_id            BIGINT        NOT NULL REFERENCES portfolio(id),
    methodology             VARCHAR(50)   NOT NULL,
    confidence_level        NUMERIC(5,4)  NOT NULL,
    horizon_days            INT           NOT NULL,
    calculation_timestamp   TIMESTAMP     NOT NULL DEFAULT now(),
    market_data_as_of       DATE          NOT NULL,
    portfolio_value         NUMERIC(24,6) NOT NULL,
    var_amount              NUMERIC(24,6) NOT NULL,
    expected_shortfall      NUMERIC(24,6) NOT NULL,
    observation_count       INT           NOT NULL,
    status                  VARCHAR(20)   NOT NULL,
    request_params          TEXT
);

CREATE INDEX idx_risk_calc_portfolio ON risk_calculation(portfolio_id);