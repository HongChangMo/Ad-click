package com.adclick.click.application;

import com.adclick.click.application.info.ReconciliationInfo;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.management.application.BalanceFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ClickReconciliationFacadeTest {

    @Mock ClickEventRepository clickEventRepository;
    @Mock BalanceFacade balanceFacade;

    @InjectMocks ClickReconciliationFacade reconciliationFacade;

    @Test
    void reconcile_invalidates_duplicate_ip_clicks_and_refunds_balance() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 20, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 21, 0, 0);
        ClickEvent first = ClickEvent.validAt(1L, "1.2.3.4", "anon-1", from.plusMinutes(1));
        ClickEvent duplicate1 = ClickEvent.validAt(1L, "1.2.3.4", "anon-2", from.plusMinutes(2));
        ClickEvent duplicate2 = ClickEvent.validAt(1L, "1.2.3.4", "anon-3", from.plusMinutes(3));
        ClickEvent otherIp = ClickEvent.validAt(1L, "5.6.7.8", "anon-4", from.plusMinutes(4));
        ClickEvent otherAd = ClickEvent.validAt(2L, "1.2.3.4", "anon-5", from.plusMinutes(5));
        given(clickEventRepository.findValidEventsBetween(from, to))
                .willReturn(List.of(first, duplicate1, duplicate2, otherIp, otherAd));
        given(clickEventRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        ReconciliationInfo result = reconciliationFacade.reconcile(from, to);

        assertThat(result.invalidatedCount()).isEqualTo(2);
        assertThat(result.refundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(first.isValid()).isTrue();
        assertThat(duplicate1.isValid()).isFalse();
        assertThat(duplicate1.getInvalidReason()).isEqualTo("DUPLICATE_IP");
        assertThat(duplicate2.isValid()).isFalse();
        assertThat(otherIp.isValid()).isTrue();
        assertThat(otherAd.isValid()).isTrue();
        verify(balanceFacade, times(2)).refund(1L, BigDecimal.valueOf(50));

        ArgumentCaptor<List<ClickEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickEventRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(duplicate1, duplicate2);
    }

    @Test
    void reconcile_does_not_refund_when_no_duplicates_exist() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 20, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 21, 0, 0);
        given(clickEventRepository.findValidEventsBetween(from, to)).willReturn(List.of(
                ClickEvent.validAt(1L, "1.2.3.4", "anon-1", from.plusMinutes(1)),
                ClickEvent.validAt(1L, "5.6.7.8", "anon-2", from.plusMinutes(2))
        ));
        given(clickEventRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        ReconciliationInfo result = reconciliationFacade.reconcile(from, to);

        assertThat(result.invalidatedCount()).isZero();
        assertThat(result.refundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(balanceFacade);
        verify(clickEventRepository).saveAll(List.of());
    }
}
