package com.adclick.click.application;

import com.adclick.click.domain.ReconciliationLockPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ScheduledClickReconciliationJob.class);
    private static final String LOCK_KEY = "scheduled";

    private final ClickReconciliationRunner runner;
    private final ReconciliationLockPort lockPort;
    private final Duration window;
    private final Duration lag;

    public ScheduledClickReconciliationJob(
            ClickReconciliationRunner runner,
            ReconciliationLockPort lockPort,
            @Value("${adclick.click.reconciliation.runner.window-minutes:10}") long windowMinutes,
            @Value("${adclick.click.reconciliation.runner.lag-seconds:30}") long lagSeconds) {
        this.runner = runner;
        this.lockPort = lockPort;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lag = Duration.ofSeconds(lagSeconds);
    }

    @Scheduled(fixedDelayString = "${adclick.click.reconciliation.runner.fixed-delay-ms:60000}")
    public void run() {
        if (!lockPort.tryLock(LOCK_KEY)) {
            log.info("click reconciliation skipped because another runner owns the lock.");
            return;
        }
        LocalDateTime to = LocalDateTime.now().minus(lag);
        LocalDateTime from = to.minus(window);
        try {
            runner.runWindow(from, to);
        } finally {
            lockPort.release(LOCK_KEY);
        }
    }
}
