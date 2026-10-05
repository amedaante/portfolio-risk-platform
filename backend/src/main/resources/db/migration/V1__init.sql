CREATE TABLE portfolio (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    base_currency VARCHAR(3)   NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE position (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    BIGINT       NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    instrument_id   VARCHAR(50)  NOT NULL,
    instrument_type VARCHAR(20)  NOT NULL, -- EQUITY, ETF, BOND, FX
    quantity        NUMERIC(20,6) NOT NULL,
    price           NUMERIC(20,6) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    valuation_date  DATE         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_position_portfolio ON position(portfolio_id);
CREATE INDEX idx_position_instrument ON position(instrument_id);

CREATE TABLE market_price (
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   VARCHAR(50)  NOT NULL,
    price_date      DATE         NOT NULL,
    price           NUMERIC(20,6) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    UNIQUE(instrument_id, price_date)
);

CREATE INDEX idx_market_price_instrument_date ON market_price(instrument_id, price_date);