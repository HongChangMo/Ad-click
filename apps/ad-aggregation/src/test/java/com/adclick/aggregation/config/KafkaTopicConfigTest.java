package com.adclick.aggregation.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaAdmin;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTopicConfig.class);

    @Test
    void clickEventTopics_is_created_when_autoCreateEnabled() {
        contextRunner
                .withPropertyValues(
                        "adclick.kafka.topics.auto-create-enabled=true",
                        "adclick.kafka.topics.click-events=click-events-test",
                        "adclick.kafka.topics.click-events-dlt=click-events-test-dlt")
                .run(context -> assertThat(context).hasSingleBean(KafkaAdmin.NewTopics.class));
    }

    @Test
    void clickEventTopics_is_not_created_when_autoCreateDisabled() {
        contextRunner
                .withPropertyValues("adclick.kafka.topics.auto-create-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(KafkaAdmin.NewTopics.class));
    }
}
