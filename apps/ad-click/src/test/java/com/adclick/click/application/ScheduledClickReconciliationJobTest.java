package com.adclick.click.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduledClickReconciliationJobTest {

    @Test
    void run_executes_recent_window_with_configured_lag() {
        ClickReconciliationRunner runner = mock(ClickReconciliationRunner.class);
        ScheduledClickReconciliationJob job = new ScheduledClickReconciliationJob(runner, 10, 30);

        LocalDateTime before = LocalDateTime.now().minusSeconds(30).minusMinutes(10);
        job.run();
        LocalDateTime after = LocalDateTime.now().minusSeconds(30);

        org.mockito.ArgumentCaptor<LocalDateTime> fromCaptor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.ArgumentCaptor<LocalDateTime> toCaptor =
                org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(runner).runWindow(fromCaptor.capture(), toCaptor.capture());

        LocalDateTime from = fromCaptor.getValue();
        LocalDateTime to = toCaptor.getValue();
        assertThat(from).isBetween(before.minusSeconds(1), before.plusSeconds(1));
        assertThat(to).isBetween(after.minusSeconds(1), after.plusSeconds(1));
        assertThat(Duration.between(from, to)).isEqualTo(Duration.ofMinutes(10));
    }
}
