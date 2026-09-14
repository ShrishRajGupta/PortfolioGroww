package com.example.demo.repository;

import com.example.demo.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /** Pending events in insertion order; the relay publishes them in this order. */
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.id asc")
    List<OutboxEvent> findUnpublished(Pageable page);

    long countByPublishedAtIsNull();

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);
}
