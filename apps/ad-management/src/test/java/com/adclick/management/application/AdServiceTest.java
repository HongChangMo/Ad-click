package com.adclick.management.application;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.infrastructure.AdJpaRepository;
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
class AdServiceTest {

    @Mock
    private AdJpaRepository adRepository;

    @InjectMocks
    private AdService adService;

    @Test
    void register_returns_active_ad() {
        Ad ad = Ad.of(1L, "Summer Sale");
        given(adRepository.save(any(Ad.class))).willReturn(ad);

        Ad result = adService.register(1L, "Summer Sale");

        assertThat(result.getStatus()).isEqualTo(AdStatus.ACTIVE);
        assertThat(result.getName()).isEqualTo("Summer Sale");
        assertThat(result.getAdvertiserId()).isEqualTo(1L);
    }

    @Test
    void changeStatus_updates_status_to_paused() {
        Ad ad = Ad.of(1L, "Summer Sale");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));

        Ad result = adService.changeStatus(1L, AdStatus.PAUSED);

        assertThat(result.getStatus()).isEqualTo(AdStatus.PAUSED);
    }

    @Test
    void changeStatus_throws_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adService.changeStatus(999L, AdStatus.PAUSED))
                .isInstanceOf(AdNotFoundException.class);
    }
}
