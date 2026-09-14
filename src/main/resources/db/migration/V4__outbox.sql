-- V4: transactional outbox.
-- A trade's TradeExecuted event is written here in the SAME transaction as the trade and its
-- position update; OutboxRelay publishes pending rows to Kafka afterwards and stamps published_at.
-- Delivery is at-least-once: event_id is the consumer-side deduplication key.

CREATE TABLE outbox_event (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(36)   NOT NULL,
    aggregate_type VARCHAR(64)   NOT NULL,
    aggregate_id   BIGINT        NOT NULL,
    event_type     VARCHAR(64)   NOT NULL,
    message_key    VARCHAR(64)   NOT NULL,
    payload        VARCHAR(4000) NOT NULL,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6)   NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(512)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    -- the relay's poll: pending rows in insertion order
    KEY idx_outbox_pending (published_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
