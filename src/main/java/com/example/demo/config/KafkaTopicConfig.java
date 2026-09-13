package com.example.demo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the trade-executed topic so Spring's KafkaAdmin creates it at startup. Tied to the relay
 * switch: where no relay runs (dev by default) nothing should touch the broker at boot.
 */
@Configuration
@ConditionalOnProperty(name = "app.outbox.relay-enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic tradeExecutedTopic(@Value("${app.kafka.topics.trade-executed}") String name,
                                       @Value("${app.kafka.topic-partitions:3}") int partitions,
                                       @Value("${app.kafka.topic-replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
