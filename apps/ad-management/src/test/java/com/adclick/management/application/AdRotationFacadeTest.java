package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdRotationQueuePort;
import com.adclick.management.domain.AdStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdRotationFacadeTest {

    @Mock AdRepository adRepository;
    @Mock AdRotationQueuePort queuePort;

    @InjectMocks AdRotationFacade adRotationFacade;

    @Test
    void getNextAd_returns_ad_and_rpushes_back_for_round_robin() {
        Ad ad = mock(Ad.class);
        given(ad.getId()).willReturn(1L);
        given(ad.getName()).willReturn("Summer Sale");
        given(ad.getStatus()).willReturn(AdStatus.ACTIVE);

        given(queuePort.poll()).willReturn(Optional.of(1L));
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        AdInfo result = adRotationFacade.getNextAd();

        assertThat(result.id()).isEqualTo(1L);
        verify(queuePort).offer(1L);
    }

    @Test
    void getNextAd_rebuilds_queue_when_empty_then_returns_ad() {
        Ad ad = mock(Ad.class);
        given(ad.getId()).willReturn(1L);
        given(ad.getName()).willReturn("Summer Sale");
        given(ad.getStatus()).willReturn(AdStatus.ACTIVE);

        given(queuePort.poll())
                .willReturn(Optional.empty())
                .willReturn(Optional.of(1L));
        given(queuePort.tryRebuildLock()).willReturn(true);
        given(adRepository.findAllActiveIds()).willReturn(List.of(1L, 2L));
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        AdInfo result = adRotationFacade.getNextAd();

        assertThat(result.id()).isEqualTo(1L);
        verify(adRepository).findAllActiveIds();
        verify(queuePort, times(2)).poll();
    }

    @Test
    void getNextAd_falls_back_to_db_random_on_valkey_exception() {
        Ad ad = mock(Ad.class);
        given(ad.getId()).willReturn(1L);
        given(ad.getName()).willReturn("Summer Sale");
        given(ad.getStatus()).willReturn(AdStatus.ACTIVE);

        given(queuePort.poll()).willThrow(new RuntimeException("Valkey connection refused"));
        given(adRepository.findRandomActive()).willReturn(Optional.of(ad));

        AdInfo result = adRotationFacade.getNextAd();

        assertThat(result.id()).isEqualTo(1L);
        verify(adRepository).findRandomActive();
    }

    @Test
    void getNextAd_throws_NoActiveAdException_when_no_active_ads() {
        given(queuePort.poll()).willReturn(Optional.empty());
        given(queuePort.tryRebuildLock()).willReturn(true);
        given(adRepository.findAllActiveIds()).willReturn(List.of());

        assertThatThrownBy(() -> adRotationFacade.getNextAd())
                .isInstanceOf(NoActiveAdException.class);
    }
}
