package com.adclick.management.application;

import com.adclick.management.application.info.BalanceInfo;
import com.adclick.management.domain.*;
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
class BalanceFacadeTest {

    @Mock AdRepository adRepository;
    @Mock AdBalanceRepository adBalanceRepository;
    @Mock BalanceTransactionRepository transactionRepository;

    @InjectMocks BalanceFacade balanceFacade;

    @Test
    void charge_creates_new_balance_when_none_exists() {
        Ad ad = Ad.of(1L, "Summer Sale");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.empty());
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BalanceInfo result = balanceFacade.charge(1L, BigDecimal.valueOf(1000));

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void charge_adds_to_existing_balance() {
        Ad ad = Ad.of(1L, "Summer Sale");
        AdBalance existing = AdBalance.of(1L);
        existing.add(BigDecimal.valueOf(500));
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.of(existing));
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BalanceInfo result = balanceFacade.charge(1L, BigDecimal.valueOf(300));

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.valueOf(800));
    }

    @Test
    void charge_exhausted_ad_activates_it() {
        Ad ad = Ad.of(1L, "Summer Sale");
        ad.changeStatus(AdStatus.EXHAUSTED);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.empty());
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        balanceFacade.charge(1L, BigDecimal.valueOf(1000));

        assertThat(ad.getStatus()).isEqualTo(AdStatus.ACTIVE);
        verify(adRepository).save(ad);
    }

    @Test
    void charge_paused_ad_keeps_paused() {
        Ad ad = Ad.of(1L, "Summer Sale");
        ad.changeStatus(AdStatus.PAUSED);
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.empty());
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        balanceFacade.charge(1L, BigDecimal.valueOf(1000));

        assertThat(ad.getStatus()).isEqualTo(AdStatus.PAUSED);
        verify(adRepository, never()).save(ad);
    }

    @Test
    void charge_throws_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> balanceFacade.charge(999L, BigDecimal.valueOf(1000)))
                .isInstanceOf(AdNotFoundException.class);
    }

    @Test
    void getBalance_returns_zero_when_no_balance_exists() {
        Ad ad = Ad.of(1L, "Summer Sale");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdId(1L)).willReturn(Optional.empty());

        BalanceInfo result = balanceFacade.getBalance(1L);

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getBalance_throws_when_ad_not_found() {
        given(adRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> balanceFacade.getBalance(999L))
                .isInstanceOf(AdNotFoundException.class);
    }
}
