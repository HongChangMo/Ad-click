package com.adclick.aggregation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@ConditionalOnProperty(
        prefix = "adclick.kafka.topics",
        name = "auto-create-enabled",
        havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public KafkaAdmin.NewTopics clickEventTopics(
            @Value("${adclick.kafka.topics.click-events:ad-click-events}") String clickEventsTopic,
            @Value("${adclick.kafka.topics.click-events-dlt:ad-click-events-dlt}") String dltTopic,
            @Value("${adclick.kafka.topics.partitions:3}") int partitions,
            @Value("${adclick.kafka.topics.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(clickEventsTopic).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(dltTopic).partitions(partitions).replicas(replicas).build());
    }
}
