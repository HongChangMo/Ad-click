package com.adclick.management.application;

import com.adclick.management.application.info.BalanceInfo;
import com.adclick.management.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BalanceFacade {

    private final AdRepository adRepository;
    private final AdBalanceRepository adBalanceRepository;
    private final BalanceTransactionRepository transactionRepository;

    public BalanceFacade(AdRepository adRepository,
                         AdBalanceRepository adBalanceRepository,
                         BalanceTransactionRepository transactionRepository) {
        this.adRepository = adRepository;
        this.adBalanceRepository = adBalanceRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public BalanceInfo charge(Long adId, BigDecimal amount) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));

        AdBalance adBalance = adBalanceRepository.findByAdId(adId)
                .orElseGet(() -> AdBalance.of(adId));

        adBalance.add(amount);
        adBalanceRepository.save(adBalance);

        transactionRepository.save(BalanceTransaction.of(adId, amount, TransactionType.CHARGE));

        if (ad.getStatus() == AdStatus.EXHAUSTED) {
            ad.changeStatus(AdStatus.ACTIVE);
            adRepository.save(ad);
        }

        return BalanceInfo.from(adBalance);
    }

    @Transactional(readOnly = true)
    public BalanceInfo getBalance(Long adId) {
        adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));

        return adBalanceRepository.findByAdId(adId)
                .map(BalanceInfo::from)
                .orElse(new BalanceInfo(adId, BigDecimal.ZERO));
    }
}
