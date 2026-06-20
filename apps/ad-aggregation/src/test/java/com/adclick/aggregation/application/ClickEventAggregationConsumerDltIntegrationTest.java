package com.adclick.aggregation.application;

import com.adclick.aggregation.TestApplication;
import com.adclick.aggregation.message.ClickEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@SpringBootTest(classes = TestApplication.class)
@EmbeddedKafka(
        partitions = 1,
        topics = {"ad-click-events-dlt-test", "ad-click-events-dlt-test-dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "adclick.kafka.topics.click-events=ad-click-events-dlt-test",
        "adclick.kafka.topics.click-events-dlt=ad-click-events-dlt-test-dlt",
        "adclick.kafka.consumer.dlt.retry-interval-ms=0",
        "adclick.kafka.consumer.dlt.max-attempts=1",
        "spring.kafka.consumer.group-id=ad-click-aggregation-dlt-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.enable-auto-commit=false",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.adclick.*",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.adclick.aggregation.message.ClickEventMessage",
        "spring.kafka.listener.type=batch",
        "spring.kafka.listener.ack-mode=manual",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "logging.level.kafka=WARN",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.org.apache.zookeeper=WARN",
        "logging.level.org.springframework.kafka=WARN"
})
@Testcontainers
class ClickEventAggregationConsumerDltIntegrationTest {

    private static final String TOPIC = "ad-click-events-dlt-test";
    private static final String DLT_TOPIC = "ad-click-events-dlt-test-dlt";

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
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    ClickAggregationService aggregationService;

    @Test
    void failed_batch_is_published_to_dlt_after_retry_exhaustion() throws Exception {
        given(aggregationService.aggregateAll(anyList()))
                .willThrow(new IllegalStateException("database unavailable"));
        ClickEventMessage message = new ClickEventMessage(
                200L,
                10L,
                "1.2.3.4",
                "anon-id",
                true,
                null,
                LocalDateTime.of(2026, 6, 20, 12, 0));

        try (Consumer<String, ClickEventMessage> consumer = dltConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, DLT_TOPIC);

            kafkaTemplate.send(TOPIC, "10", message).get();

            ConsumerRecord<String, ClickEventMessage> record =
                    KafkaTestUtils.getSingleRecord(consumer, DLT_TOPIC, Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo("10");
            assertThat(record.value().clickEventId()).isEqualTo(200L);
        }
    }

    private Consumer<String, ClickEventMessage> dltConsumer() {
        JsonDeserializer<ClickEventMessage> valueDeserializer =
                new JsonDeserializer<>(ClickEventMessage.class, new ObjectMapper().findAndRegisterModules(), false);
        valueDeserializer.addTrustedPackages("com.adclick.*");
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "ad-click-aggregation-dlt-test-reader",
                "false",
                embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                valueDeserializer)
                .createConsumer();
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
