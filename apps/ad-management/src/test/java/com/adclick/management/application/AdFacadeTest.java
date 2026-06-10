package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdFacadeTest {

    @Mock
    private AdRepository adRepository;

    @InjectMocks
    private AdFacade adFacade;

    @Test
    void register_returns_active_ad_info() {
        Ad ad = Ad.of(1L, "Summer Sale");
        given(adRepository.save(any(Ad.class))).willReturn(ad);

        AdInfo result = adFacade.register(1L, "Summer Sale");

        assertThat(result.status()).isEqualTo(AdStatus.ACTIVE);
        assertThat(result.name()).isEqualTo("Summer Sale");
    }

    @Test
    void changeStatus_updates_status_to_paused() {
        Ad ad = Ad.of(1L, "Summer Sale");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        AdInfo result = adFacade.changeStatus(1L, AdStatus.PAUSED);

        assertThat(result.status()).isEqualTo(AdStatus.PAUSED);
    }

    @Test
    void changeStatus_throws_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adFacade.changeStatus(999L, AdStatus.PAUSED))
                .isInstanceOf(AdNotFoundException.class);
    }
}
