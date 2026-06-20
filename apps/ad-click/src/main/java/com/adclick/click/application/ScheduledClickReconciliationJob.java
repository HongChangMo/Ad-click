package com.adclick.click.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(
        prefix = "adclick.click.reconciliation.runner",
        name = "enabled",
        havingValue = "true")
public class ScheduledClickReconciliationJob {

    private final ClickReconciliationRunner runner;
    private final Duration window;
    private final Duration lag;

    public ScheduledClickReconciliationJob(
            ClickReconciliationRunner runner,
            @Value("${adclick.click.reconciliation.runner.window-minutes:10}") long windowMinutes,
            @Value("${adclick.click.reconciliation.runner.lag-seconds:30}") long lagSeconds) {
        this.runner = runner;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lag = Duration.ofSeconds(lagSeconds);
    }

    @Scheduled(fixedDelayString = "${adclick.click.reconciliation.runner.fixed-delay-ms:60000}")
    public void run() {
        LocalDateTime to = LocalDateTime.now().minus(lag);
        LocalDateTime from = to.minus(window);
        runner.runWindow(from, to);
    }
}
