package com.adclick.management.application;

import com.adclick.management.application.info.BalanceInfo;
import com.adclick.management.domain.*;
import com.adclick.management.domain.AdRotationQueuePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BalanceFacade {

    private final AdRepository adRepository;
    private final AdBalanceRepository adBalanceRepository;
    private final BalanceTransactionRepository transactionRepository;
    private final AdRotationQueuePort queuePort;

    public BalanceFacade(AdRepository adRepository,
                         AdBalanceRepository adBalanceRepository,
                         BalanceTransactionRepository transactionRepository,
                         AdRotationQueuePort queuePort) {
        this.adRepository = adRepository;
        this.adBalanceRepository = adBalanceRepository;
        this.transactionRepository = transactionRepository;
        this.queuePort = queuePort;
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
            queuePort.offer(adId); // EXHAUSTED → ACTIVE 전환 시 Valkey 큐 재진입
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
