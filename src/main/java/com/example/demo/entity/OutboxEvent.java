package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One domain event waiting to be (or already) published to Kafka. Written in the same transaction
 * as the state change it describes — the transactional outbox pattern. Schema: db/migration V4.
 */
@Data
@Entity
@Table(name = "outbox_event")
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable identity of the event across redeliveries; consumers deduplicate on it. */
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    /** Kafka message key: events with the same key land on one partition and stay ordered. */
    @Column(name = "message_key", nullable = false, length = 64)
    private String messageKey;

    /** JSON body, published verbatim. */
    @Column(nullable = false, length = 4000)
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 512)
    private String lastError;
}
