-- V1: baseline of the schema Hibernate previously generated via ddl-auto=update.
-- From here on, schema changes are versioned migrations; Hibernate only validates.
-- Existing databases created by ddl-auto adopt this via spring.flyway.baseline-on-migrate.

CREATE TABLE user_account (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  DATETIME(6)  DEFAULT NULL,
    email       VARCHAR(255) DEFAULT NULL,
    name        VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(255) DEFAULT NULL,
    open_price       DOUBLE       DEFAULT NULL,
    close_price      DOUBLE       DEFAULT NULL,
    high_price       DOUBLE       DEFAULT NULL,
    low_price        DOUBLE       DEFAULT NULL,
    settlement_price DOUBLE       DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE trades (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    created_at      DATETIME(6)  DEFAULT NULL,
    price           DOUBLE       DEFAULT NULL,
    quantity        INT          DEFAULT NULL,
    trade_type      VARCHAR(255) DEFAULT NULL,
    stock_id        BIGINT       NOT NULL,
    user_account_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_trades_stock (stock_id),
    KEY idx_trades_user_account (user_account_id),
    CONSTRAINT fk_trades_stock        FOREIGN KEY (stock_id)        REFERENCES stock (id),
    CONSTRAINT fk_trades_user_account FOREIGN KEY (user_account_id) REFERENCES user_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
