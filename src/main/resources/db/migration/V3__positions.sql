-- V3: materialized positions.
-- One row per (user, stock): units held, weighted-average cost, cumulative realized P&L.
-- Maintained in the same transaction as each trade insert; `version` drives optimistic locking.
-- The ledger (trades) stays the source of truth; V3.1 (Java) backfills this table from it.

CREATE TABLE positions (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT        NOT NULL,
    stock_id        BIGINT        NOT NULL,
    net_quantity    BIGINT        NOT NULL,
    avg_cost        DECIMAL(19,8) NOT NULL,
    realized_pnl    DECIMAL(19,8) NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_positions_user_stock UNIQUE (user_account_id, stock_id),
    CONSTRAINT fk_positions_user  FOREIGN KEY (user_account_id) REFERENCES user_account (id),
    CONSTRAINT fk_positions_stock FOREIGN KEY (stock_id)        REFERENCES stock (id),
    CONSTRAINT ck_positions_net_quantity_non_negative CHECK (net_quantity >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Ledger indexes for the access paths that remain on trades:
--   per-(user, stock) scans (reconciliation, drift) and per-user chronological replay.
-- The composite (user_account_id, stock_id) index leads with user_account_id, so it also serves the
-- user FK; the single-column index it supersedes is removed by V3.2 (Java), which looks the index up
-- by its columns because its NAME differs between databases created by V1 and databases adopted
-- from Hibernate ddl-auto via baseline-on-migrate. Note: on adopted databases InnoDB drops the
-- auto-generated FK index by itself as soon as the composite below exists (documented MySQL
-- behaviour), so V3.2 finds nothing to do there.
CREATE INDEX idx_trades_user_stock   ON trades (user_account_id, stock_id);
CREATE INDEX idx_trades_user_created ON trades (user_account_id, created_at, id);
