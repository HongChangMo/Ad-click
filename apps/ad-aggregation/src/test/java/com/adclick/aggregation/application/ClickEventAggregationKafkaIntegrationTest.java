package com.adclick.aggregation.application;

import com.adclick.aggregation.TestApplication;
import com.adclick.aggregation.domain.ClickDailyStats;
import com.adclick.aggregation.infrastructure.ClickDailyStatsJpaRepository;
import com.adclick.aggregation.infrastructure.ProcessedClickEventJpaRepository;
import com.adclick.aggregation.message.ClickEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@EmbeddedKafka(
        partitions = 1,
        topics = "ad-click-events-integration",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "adclick.kafka.topics.click-events=ad-click-events-integration",
        "spring.kafka.consumer.group-id=ad-click-aggregation-integration",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.enable-auto-commit=false",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.adclick.*",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.adclick.aggregation.message.ClickEventMessage",
        "spring.kafka.listener.ack-mode=manual",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "logging.level.kafka=WARN",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.org.apache.zookeeper=WARN",
        "logging.level.org.springframework.kafka=WARN"
})
@Testcontainers
class ClickEventAggregationKafkaIntegrationTest {

    private static final String TOPIC = "ad-click-events-integration";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    KafkaTemplate<String, ClickEventMessage> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    ProcessedClickEventJpaRepository processedRepository;

    @Autowired
    ClickDailyStatsJpaRepository statsRepository;

    @BeforeEach
    void waitForListenerAssignment() {
        registry.getListenerContainers()
                .forEach(container -> ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic()));
    }

    @Test
    void consumer_updates_daily_stats_once_when_duplicate_kafka_message_is_redelivered() throws Exception {
        LocalDateTime clickedAt = LocalDateTime.of(2026, 6, 20, 12, 0);
        ClickEventMessage valid = new ClickEventMessage(
                100L,
                10L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                clickedAt);
        ClickEventMessage duplicate = new ClickEventMessage(
                100L,
                10L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                clickedAt);
        ClickEventMessage invalid = new ClickEventMessage(
                101L,
                10L,
                "1.2.3.4",
                "anon-id",
                false,
                "DUPLICATE_IP",
                clickedAt);

        kafkaTemplate.send(TOPIC, "10", valid).get();
        kafkaTemplate.send(TOPIC, "10", duplicate).get();
        kafkaTemplate.send(TOPIC, "10", invalid).get();

        awaitProcessedCount(2);

        ClickDailyStats stats = statsRepository.findByAdIdAndStatsDate(10L, LocalDate.of(2026, 6, 20));
        assertThat(stats.getValidCount()).isEqualTo(1);
        assertThat(stats.getInvalidCount()).isEqualTo(1);
        assertThat(processedRepository.existsById(100L)).isTrue();
        assertThat(processedRepository.existsById(101L)).isTrue();
    }

    private void awaitProcessedCount(long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (processedRepository.count() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(processedRepository.count()).isEqualTo(expected);
    }

    @TestConfiguration
    static class KafkaProducerTestConfig {

        @Bean
        @Primary
        ProducerFactory<String, ClickEventMessage> producerFactory(
                @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
            JsonSerializer<ClickEventMessage> valueSerializer =
                    new JsonSerializer<>(new ObjectMapper().findAndRegisterModules());
            return new DefaultKafkaProducerFactory<>(
                    Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                    new StringSerializer(),
                    valueSerializer);
        }

        @Bean
        @Primary
        KafkaTemplate<String, ClickEventMessage> kafkaTemplate(
                ProducerFactory<String, ClickEventMessage> producerFactory) {
            return new KafkaTemplate<>(producerFactory);
        }
    }
}
