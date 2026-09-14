package com.example.demo;

import com.example.demo.dto.TradeRequestDTO;
import com.example.demo.dto.TradeResponseDTO;
import com.example.demo.entity.OutboxEvent;
import com.example.demo.entity.Stock;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.enums.TradeType;
import com.example.demo.event.TradeExecutedEvent;
import com.example.demo.messaging.OutboxRelay;
import com.example.demo.messaging.TradeExecutedConsumer;
import com.example.demo.repository.OutboxRepository;
import com.example.demo.repository.StockRepository;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.service.TradeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The whole event loop against a real (embedded) Kafka broker: a booked trade leaves an outbox row,
 * the relay publishes it with the eventId header and stamps it published, the downstream consumer
 * processes it once — and ignores a redelivery of the same event.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "app.kafka.topics.trade-executed=" + OutboxKafkaIntegrationTest.TOPIC,
        "app.kafka.consumer-enabled=true",
        "app.kafka.consumer-group=it-portfolio-events",
        "app.outbox.relay-enabled=true",
        "app.outbox.poll-ms=200",
        "app.outbox.send-timeout-ms=10000",
        "app.kafka.topic-partitions=1"
})
@EmbeddedKafka(partitions = 1, topics = OutboxKafkaIntegrationTest.TOPIC)
@DirtiesContext
class OutboxKafkaIntegrationTest {

    static final String TOPIC = "trade-executed-it";

    @Autowired
    private TradeService tradeService;
    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private TradeExecutedConsumer downstream;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private StockRepository stocks;
    @Autowired
    private EmbeddedKafkaBroker broker;

    private static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("timed out waiting for: " + what);
            }
            Thread.sleep(50);
        }
    }

    @Test
    void bookedTrade_isPublishedOnce_andConsumedOnce_evenWhenRedelivered() throws Exception {
        // a test-side consumer subscribed from the start, in its own group
        Map<String, Object> props = KafkaTestUtils.consumerProps("it-observer", "true", broker);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);   // keys are user ids as text
        Consumer<String, String> observer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        broker.consumeFromAnEmbeddedTopic(observer, TOPIC);

        BigDecimal p = new BigDecimal("50.0000");
        UserAccount user = users.save(UserAccount.builder().name("Eve").email("eve@example.com").build());
        Stock stock = stocks.save(Stock.builder().name("EVNT").openPrice(p).closePrice(p).highPrice(p).lowPrice(p).settlementPrice(p).build());

        // 1) booking writes the outbox row in the same transaction
        TradeResponseDTO booked = tradeService.recordTrade(
                new TradeRequestDTO("it-key-1", user.getId(), stock.getId(), TradeType.BUY, 3, null));
        List<OutboxEvent> rows = outbox.findByAggregateTypeAndAggregateId("Trade", booked.getTradeId());
        assertEquals(1, rows.size());
        OutboxEvent row = rows.get(0);
        assertEquals(String.valueOf(user.getId()), row.getMessageKey());

        // 2) the relay publishes and stamps it
        awaitTrue("outbox row published", () -> outbox.findById(row.getId()).orElseThrow().getPublishedAt() != null);
        assertEquals(0, outbox.countByPublishedAtIsNull());
        assertEquals(0, outbox.findById(row.getId()).orElseThrow().getAttempts());

        // 3) the message on the wire carries the header + payload
        ConsumerRecord<String, String> onWire = KafkaTestUtils.getSingleRecord(observer, TOPIC, Duration.ofSeconds(20));
        assertEquals(String.valueOf(user.getId()), onWire.key());
        assertEquals(row.getEventId(), new String(onWire.headers().lastHeader(OutboxRelay.HEADER_EVENT_ID).value(), StandardCharsets.UTF_8));
        TradeExecutedEvent event = objectMapper.readValue(onWire.value(), TradeExecutedEvent.class);
        assertEquals(booked.getTradeId(), event.tradeId());
        assertEquals("it-key-1", event.clientTradeId());
        assertEquals(TradeType.BUY, event.tradeType());
        assertEquals(3, event.quantity());
        assertEquals(new BigDecimal("50.0000"), event.price());

        // 4) the downstream consumer processed it exactly once
        awaitTrue("downstream processed", () -> downstream.processedCount() == 1);

        // 5) redeliver the very same event (what an at-least-once relay may do after a crash) -> ignored
        ProducerRecord<String, String> again = new ProducerRecord<>(TOPIC, onWire.key(), onWire.value());
        onWire.headers().forEach(h -> again.headers().add(h));
        kafkaTemplate.send(again).get();
        awaitTrue("duplicate seen", () -> downstream.duplicateCount() == 1);
        assertEquals(1, downstream.processedCount(), "no double processing");

        observer.close();
    }
}
