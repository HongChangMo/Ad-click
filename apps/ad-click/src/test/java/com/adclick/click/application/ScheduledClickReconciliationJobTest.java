package com.adclick.click.application;

import com.adclick.click.domain.ReconciliationLockPort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledClickReconciliationJobTest {

    @Test
    void run_executes_recent_window_with_configured_lag() {
        ClickReconciliationRunner runner = mock(ClickReconciliationRunner.class);
        ReconciliationLockPort lockPort = mock(ReconciliationLockPort.class);
        when(lockPort.tryLock("scheduled")).thenReturn(true);
        ScheduledClickReconciliationJob job = new ScheduledClickReconciliationJob(runner, lockPort, 10, 30);

        LocalDateTime before = LocalDateTime.now().minusSeconds(30).minusMinutes(10);
        job.run();
        LocalDateTime after = LocalDateTime.now().minusSeconds(30);

        org.mockito.ArgumentCaptor<LocalDateTime> fromCaptor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> toCaptor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(runner).runWindow(fromCaptor.capture(), toCaptor.capture());
        verify(lockPort).release("scheduled");

        LocalDateTime from = fromCaptor.getValue();
        LocalDateTime to = toCaptor.getValue();
        assertThat(from).isBetween(before.minusSeconds(1), before.plusSeconds(1));
        assertThat(to).isBetween(after.minusSeconds(1), after.plusSeconds(1));
        assertThat(Duration.between(from, to)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void run_skips_when_lock_is_already_held() {
        ClickReconciliationRunner runner = mock(ClickReconciliationRunner.class);
        ReconciliationLockPort lockPort = mock(ReconciliationLockPort.class);
        when(lockPort.tryLock("scheduled")).thenReturn(false);
        ScheduledClickReconciliationJob job = new ScheduledClickReconciliationJob(runner, lockPort, 10, 30);

        job.run();

        verify(runner, never()).runWindow(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(lockPort, never()).release("scheduled");
    }
}
