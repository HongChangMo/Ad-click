package com.adclick.aggregation.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerDltConfig {

    @Bean
    public DefaultErrorHandler clickEventConsumerErrorHandler(
            KafkaOperations<Object, Object> kafkaOperations,
            @Value("${adclick.kafka.topics.click-events-dlt:ad-click-events-dlt}") String dltTopic,
            @Value("${adclick.kafka.consumer.dlt.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${adclick.kafka.consumer.dlt.max-attempts:3}") long maxAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        long retryCount = Math.max(0, maxAttempts - 1);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryCount));
    }
}
