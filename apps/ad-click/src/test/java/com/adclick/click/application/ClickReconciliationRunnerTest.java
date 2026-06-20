package com.adclick.click.application;

import com.adclick.click.application.info.ReconciliationInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClickReconciliationRunnerTest {

    @Test
    void runWindow_delegates_to_reconciliation_facade() {
        ClickReconciliationFacade facade = mock(ClickReconciliationFacade.class);
        ClickReconciliationRunner runner = new ClickReconciliationRunner(facade);
        LocalDateTime from = LocalDateTime.of(2026, 6, 20, 10, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 20, 10, 10);
        ReconciliationInfo expected = new ReconciliationInfo(from, to, 2, BigDecimal.valueOf(100));
        when(facade.reconcile(from, to)).thenReturn(expected);

        ReconciliationInfo result = runner.runWindow(from, to);

        assertThat(result).isEqualTo(expected);
        verify(facade).reconcile(from, to);
    }
}
