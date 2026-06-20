package com.adclick.management.application;

import com.adclick.management.application.info.BalanceInfo;
import com.adclick.management.domain.*;
import org.junit.jupiter.api.Test;
import com.adclick.management.domain.AdRotationQueuePort;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BalanceFacadeTest {

    @Mock AdRepository adRepository;
    @Mock AdBalanceRepository adBalanceRepository;
    @Mock BalanceTransactionRepository transactionRepository;
    @Mock AdRotationQueuePort queuePort;

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
        verify(queuePort).offer(1L);
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
        verify(queuePort, never()).offer(any());
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

    @Test
    void deduct_view_subtracts_amount_and_records_view_transaction() {
        AdBalance balance = AdBalance.of(1L);
        balance.add(BigDecimal.valueOf(100));
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.of(balance));
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        balanceFacade.deduct(1L, BigDecimal.TEN, TransactionType.VIEW);

        assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(90));
        verify(transactionRepository).save(argThat(t ->
                t.getType() == TransactionType.VIEW
                && t.getAmount().compareTo(BigDecimal.TEN) == 0));
    }

    @Test
    void deduct_click_records_click_transaction() {
        AdBalance balance = AdBalance.of(1L);
        balance.add(BigDecimal.valueOf(200));
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.of(balance));
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        balanceFacade.deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK);

        assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(150));
        verify(transactionRepository).save(argThat(t -> t.getType() == TransactionType.CLICK));
    }

    @Test
    void deduct_exhausts_active_ad_when_balance_reaches_zero() {
        Ad ad = Ad.of(1L, "Almost Empty Ad");
        AdBalance balance = AdBalance.of(1L);
        balance.add(BigDecimal.valueOf(50));
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.of(balance));
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        balanceFacade.deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK);

        assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(ad.getStatus()).isEqualTo(AdStatus.EXHAUSTED);
        verify(adRepository).save(ad);
        verify(queuePort).remove(1L);
    }

    @Test
    void deduct_throws_when_balance_is_insufficient() {
        AdBalance balance = AdBalance.of(1L);
        balance.add(BigDecimal.valueOf(40));
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.of(balance));

        assertThatThrownBy(() -> balanceFacade.deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(balance.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(40));
        verify(adBalanceRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deduct_throws_when_balance_row_does_not_exist() {
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> balanceFacade.deduct(1L, BigDecimal.valueOf(50), TransactionType.CLICK))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(adBalanceRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void refund_adds_balance_and_records_refund_transaction() {
        Ad ad = Ad.of(1L, "Refund Ad");
        AdBalance balance = AdBalance.of(1L);
        balance.add(BigDecimal.valueOf(100));
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.of(balance));
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BalanceInfo result = balanceFacade.refund(1L, BigDecimal.valueOf(50));

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.valueOf(150));
        verify(transactionRepository).save(argThat(t ->
                t.getType() == TransactionType.REFUND
                        && t.getAmount().compareTo(BigDecimal.valueOf(50)) == 0));
    }

    @Test
    void refund_creates_balance_when_row_does_not_exist() {
        Ad ad = Ad.of(1L, "Refund New Balance Ad");
        given(adRepository.findById(1L)).willReturn(Optional.of(ad));
        given(adBalanceRepository.findByAdIdForUpdate(1L)).willReturn(Optional.empty());
        given(adBalanceRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(transactionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BalanceInfo result = balanceFacade.refund(1L, BigDecimal.valueOf(50));

        assertThat(result.balance()).isEqualByComparingTo(BigDecimal.valueOf(50));
        verify(transactionRepository).save(argThat(t -> t.getType() == TransactionType.REFUND));
    }
}
