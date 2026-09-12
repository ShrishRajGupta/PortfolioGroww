-- V2: domain correctness.
--   * money columns DOUBLE -> DECIMAL(19,4)
--   * natural keys become unique (stock.name, user_account.email), after de-duplicating
--     rows that earlier seeders/imports created; trades are re-pointed to the surviving row
--   * trade_type restricted to BUY/SELL, quantity must be positive, timestamps not null
--   * client_trade_id: client-supplied idempotency key for POST /api/trade
-- Rows that violate a CHECK (e.g. a non-positive quantity) make this migration fail on
-- purpose: that is data an operator must look at, not something to silently rewrite.

-- ---------------------------------------------------------------- money
ALTER TABLE stock
    MODIFY open_price       DECIMAL(19,4) NOT NULL,
    MODIFY close_price      DECIMAL(19,4) NOT NULL,
    MODIFY high_price       DECIMAL(19,4) NOT NULL,
    MODIFY low_price        DECIMAL(19,4) NOT NULL,
    MODIFY settlement_price DECIMAL(19,4) NOT NULL;

ALTER TABLE trades
    MODIFY price DECIMAL(19,4) NOT NULL;

-- ---------------------------------------------------------------- user_account.email unique
UPDATE user_account SET email = CONCAT('unknown-', id, '@invalid.local') WHERE email IS NULL OR email = '';

UPDATE trades t
    JOIN user_account u ON u.id = t.user_account_id
    JOIN (SELECT email, MIN(id) AS keep_id FROM user_account GROUP BY email) k ON k.email = u.email
SET t.user_account_id = k.keep_id
WHERE u.id <> k.keep_id;

DELETE u FROM user_account u
    JOIN (SELECT email, MIN(id) AS keep_id FROM user_account GROUP BY email) k ON k.email = u.email
WHERE u.id <> k.keep_id;

ALTER TABLE user_account
    MODIFY email VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_user_account_email UNIQUE (email);

-- ---------------------------------------------------------------- stock.name unique
UPDATE stock SET name = CONCAT('UNNAMED-', id) WHERE name IS NULL OR name = '';

UPDATE trades t
    JOIN stock s ON s.id = t.stock_id
    JOIN (SELECT name, MIN(id) AS keep_id FROM stock GROUP BY name) k ON k.name = s.name
SET t.stock_id = k.keep_id
WHERE s.id <> k.keep_id;

DELETE s FROM stock s
    JOIN (SELECT name, MIN(id) AS keep_id FROM stock GROUP BY name) k ON k.name = s.name
WHERE s.id <> k.keep_id;

ALTER TABLE stock
    MODIFY name VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uk_stock_name UNIQUE (name);

-- ---------------------------------------------------------------- trades: type, quantity, timestamps
UPDATE trades SET trade_type = UPPER(TRIM(trade_type)) WHERE trade_type IS NOT NULL;
UPDATE trades       SET created_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL;
UPDATE user_account SET created_at = CURRENT_TIMESTAMP(6) WHERE created_at IS NULL;

ALTER TABLE trades
    MODIFY trade_type VARCHAR(8) NOT NULL,
    MODIFY quantity   INT        NOT NULL,
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT ck_trades_trade_type        CHECK (trade_type IN ('BUY', 'SELL')),
    ADD CONSTRAINT ck_trades_quantity_positive CHECK (quantity > 0);

ALTER TABLE user_account
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- ---------------------------------------------------------------- idempotency key
ALTER TABLE trades
    ADD COLUMN client_trade_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_trades_client_trade_id UNIQUE (client_trade_id);
