package com.adclick.management.application;

import com.adclick.management.application.info.BalanceInfo;
import com.adclick.management.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Service
public class BalanceFacade {

    private static final Logger log = LoggerFactory.getLogger(BalanceFacade.class);

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

    @Transactional
    public void deduct(Long adId, BigDecimal amount, TransactionType type) {
        AdBalance balance = adBalanceRepository.findByAdIdForUpdate(adId)
                .orElseThrow(() -> new InsufficientBalanceException(adId));
        balance.subtract(amount);
        exhaustAdIfBalanceIsZero(adId, balance);
        adBalanceRepository.save(balance);
        transactionRepository.save(BalanceTransaction.of(adId, amount, type));
    }

    @Transactional
    public BalanceInfo refund(Long adId, BigDecimal amount) {
        adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));

        AdBalance balance = adBalanceRepository.findByAdIdForUpdate(adId)
                .orElseGet(() -> AdBalance.of(adId));
        balance.add(amount);
        adBalanceRepository.save(balance);
        transactionRepository.save(BalanceTransaction.of(adId, amount, TransactionType.REFUND));
        return BalanceInfo.from(balance);
    }

    private void exhaustAdIfBalanceIsZero(Long adId, AdBalance balance) {
        if (balance.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            return;
        }

        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        if (ad.getStatus() != AdStatus.ACTIVE) {
            return;
        }

        ad.changeStatus(AdStatus.EXHAUSTED);
        adRepository.save(ad);
        afterCommit(() -> removeFromRotationQueue(adId));
    }

    private void removeFromRotationQueue(Long adId) {
        try {
            queuePort.remove(adId);
        } catch (RuntimeException e) {
            log.warn("Failed to remove exhausted ad from rotation queue. adId={}", adId, e);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
