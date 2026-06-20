package com.adclick.click.application;

import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.application.info.ClickStatsInfo;
import com.adclick.click.domain.AbuseGuardPort;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventPublisher;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.click.domain.InvalidClickReason;
import com.adclick.management.application.AdNotFoundException;
import com.adclick.management.application.BalanceFacade;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClickFacadeTest {

    @Mock AdRepository adRepository;
    @Mock BalanceFacade balanceFacade;
    @Mock ClickEventRepository clickEventRepository;
    @Mock AbuseGuardPort abuseGuardPort;
    @Mock ClickEventPublisher clickEventPublisher;

    @InjectMocks ClickFacade clickFacade;

    @Test
    void click_active_ad_deducts_50_and_records_event() {
        Ad ad = Ad.of(1L, "Test Ad");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(abuseGuardPort.checkAndMark(1L, "1.2.3.4", "anon-id")).willReturn(Optional.empty());
        ClickEvent savedEvent = ClickEvent.valid(1L, "1.2.3.4", "anon-id");
        given(clickEventRepository.save(any())).willReturn(savedEvent);

        ClickInfo result = clickFacade.click(1L, "1.2.3.4", "anon-id");

        assertThat(result.adId()).isEqualTo(1L);
        assertThat(result.isValid()).isTrue();
        verify(balanceFacade).deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK);
        verify(clickEventRepository).save(any(ClickEvent.class));
        verify(clickEventPublisher).publish(savedEvent);
    }

    @Test
    void click_duplicate_ip_records_invalid_event_without_deducting_balance() {
        Ad ad = Ad.of(1L, "Test Ad");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(abuseGuardPort.checkAndMark(1L, "1.2.3.4", "anon-id"))
                .willReturn(Optional.of(InvalidClickReason.DUPLICATE_IP));
        ClickEvent savedEvent = ClickEvent.invalid(1L, "1.2.3.4", "anon-id", InvalidClickReason.DUPLICATE_IP);
        given(clickEventRepository.save(any())).willReturn(savedEvent);

        ClickInfo result = clickFacade.click(1L, "1.2.3.4", "anon-id");

        assertThat(result.adId()).isEqualTo(1L);
        assertThat(result.isValid()).isFalse();
        assertThat(result.invalidReason()).isEqualTo("DUPLICATE_IP");
        verify(balanceFacade, never()).deduct(any(), any(), any());

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(clickEventRepository).save(captor.capture());
        assertThat(captor.getValue().isValid()).isFalse();
        assertThat(captor.getValue().getInvalidReason()).isEqualTo("DUPLICATE_IP");
        verify(clickEventPublisher).publish(savedEvent);
    }

    @Test
    void click_duplicate_anonymous_id_records_invalid_event_without_deducting_balance() {
        Ad ad = Ad.of(1L, "Test Ad");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(abuseGuardPort.checkAndMark(1L, "5.6.7.8", "anon-id"))
                .willReturn(Optional.of(InvalidClickReason.DUPLICATE_ANON));
        ClickEvent savedEvent = ClickEvent.invalid(1L, "5.6.7.8", "anon-id", InvalidClickReason.DUPLICATE_ANON);
        given(clickEventRepository.save(any())).willReturn(savedEvent);

        ClickInfo result = clickFacade.click(1L, "5.6.7.8", "anon-id");

        assertThat(result.isValid()).isFalse();
        assertThat(result.invalidReason()).isEqualTo("DUPLICATE_ANON");
        verify(balanceFacade, never()).deduct(any(), any(), any());
        verify(clickEventPublisher).publish(savedEvent);
    }

    @Test
    void stats_counts_valid_and_invalid_clicks_in_period() {
        Ad ad = Ad.of(1L, "Stats Ad");
        LocalDateTime from = LocalDateTime.of(2026, 6, 20, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 21, 0, 0);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(clickEventRepository.countByAdIdAndValidityBetween(1L, true, from, to)).willReturn(7L);
        given(clickEventRepository.countByAdIdAndValidityBetween(1L, false, from, to)).willReturn(3L);

        ClickStatsInfo result = clickFacade.stats(1L, from, to);

        assertThat(result.adId()).isEqualTo(1L);
        assertThat(result.from()).isEqualTo(from);
        assertThat(result.to()).isEqualTo(to);
        assertThat(result.validCount()).isEqualTo(7);
        assertThat(result.invalidCount()).isEqualTo(3);
    }

    @Test
    void stats_throws_AdNotFoundException_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clickFacade.stats(999L, null, null))
                .isInstanceOf(AdNotFoundException.class);

        verify(clickEventRepository, never()).countByAdIdAndValidityBetween(any(), anyBoolean(), any(), any());
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clickFacade.click(999L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
        verify(clickEventRepository, never()).save(any());
        verify(clickEventPublisher, never()).publish(any());
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_is_paused() {
        Ad ad = Ad.of(1L, "Paused Ad");
        ad.changeStatus(AdStatus.PAUSED);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        assertThatThrownBy(() -> clickFacade.click(1L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
        verify(clickEventRepository, never()).save(any());
        verify(clickEventPublisher, never()).publish(any());
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_is_exhausted() {
        Ad ad = Ad.of(1L, "Exhausted Ad");
        ad.changeStatus(AdStatus.EXHAUSTED);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        assertThatThrownBy(() -> clickFacade.click(1L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
        verify(clickEventRepository, never()).save(any());
        verify(clickEventPublisher, never()).publish(any());
    }
}
