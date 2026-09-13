package com.example.demo.repository;

import com.example.demo.entity.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OutboxRepositoryJpaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OutboxRepository outbox;

    private OutboxEvent persist(String eventId, LocalDateTime publishedAt) {
        return em.persist(OutboxEvent.builder().eventId(eventId).aggregateType("Trade").aggregateId(1L)
                .eventType("TradeExecuted").messageKey("1").payload("{}").publishedAt(publishedAt).attempts(0).build());
    }

    @Test
    void findUnpublished_returnsPendingOnly_oldestFirst_respectingTheLimit() {
        persist("e-1", null);
        persist("e-2", LocalDateTime.now());
        persist("e-3", null);
        persist("e-4", null);
        em.flush();
        em.clear();

        List<OutboxEvent> pending = outbox.findUnpublished(PageRequest.of(0, 10));
        assertEquals(List.of("e-1", "e-3", "e-4"), pending.stream().map(OutboxEvent::getEventId).toList());
        assertEquals(3, outbox.countByPublishedAtIsNull());

        assertEquals(List.of("e-1", "e-3"),
                outbox.findUnpublished(PageRequest.of(0, 2)).stream().map(OutboxEvent::getEventId).toList());
    }

    @Test
    void createdAt_isSetOnInsert() {
        OutboxEvent e = persist("e-1", null);
        em.flush();

        assertNotNull(e.getCreatedAt());
        assertTrue(outbox.findByEventId("e-1").isPresent());
    }

    @Test
    void eventId_isUniqueAtTheDatabase() {
        outbox.saveAndFlush(OutboxEvent.builder().eventId("dup").aggregateType("Trade").aggregateId(1L)
                .eventType("TradeExecuted").messageKey("1").payload("{}").attempts(0).build());

        assertThrows(DataIntegrityViolationException.class, () ->
                outbox.saveAndFlush(OutboxEvent.builder().eventId("dup").aggregateType("Trade").aggregateId(2L)
                        .eventType("TradeExecuted").messageKey("1").payload("{}").attempts(0).build()));
    }
}
