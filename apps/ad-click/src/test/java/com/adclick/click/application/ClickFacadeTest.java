package com.adclick.click.application;

import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.management.application.AdNotFoundException;
import com.adclick.management.application.BalanceFacade;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClickFacadeTest {

    @Mock AdRepository adRepository;
    @Mock BalanceFacade balanceFacade;
    @Mock ClickEventRepository clickEventRepository;

    @InjectMocks ClickFacade clickFacade;

    @Test
    void click_active_ad_deducts_50_and_records_event() {
        Ad ad = Ad.of(1L, "Test Ad");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        ClickEvent savedEvent = ClickEvent.valid(1L, "1.2.3.4", "anon-id");
        given(clickEventRepository.save(any())).willReturn(savedEvent);

        ClickInfo result = clickFacade.click(1L, "1.2.3.4", "anon-id");

        assertThat(result.adId()).isEqualTo(1L);
        assertThat(result.isValid()).isTrue();
        verify(balanceFacade).deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK);
        verify(clickEventRepository).save(any(ClickEvent.class));
    }

    @Test
    void click_throws_AdNotFoundException_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> clickFacade.click(999L, "1.2.3.4", "anon"))
                .isInstanceOf(AdNotFoundException.class);

        verify(balanceFacade, never()).deduct(any(), any(), any());
        verify(clickEventRepository, never()).save(any());
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
    }
}
