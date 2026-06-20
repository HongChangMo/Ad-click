package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdRotationQueuePort;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class AdRotationFacade {

    private static final BigDecimal VIEW_COST = BigDecimal.TEN;

    private final AdRepository adRepository;
    private final AdRotationQueuePort queuePort;
    private final BalanceFacade balanceFacade;

    public AdRotationFacade(AdRepository adRepository,
                            AdRotationQueuePort queuePort,
                            BalanceFacade balanceFacade) {
        this.adRepository = adRepository;
        this.queuePort = queuePort;
        this.balanceFacade = balanceFacade;
    }

    @Transactional
    public AdInfo getNextAd() {
        AdInfo adInfo;
        try {
            adInfo = nextAdFromQueue()
                    .orElseGet(this::nextAdFromDb);
        } catch (NoActiveAdException e) {
            throw e;
        } catch (Exception e) {
            // Valkey 장애 Fallback
            adInfo = nextAdFromDb();
        }
        balanceFacade.deduct(adInfo.id(), VIEW_COST, TransactionType.VIEW);
        return adInfo;
    }

    private Optional<AdInfo> nextAdFromQueue() {
        Optional<Long> adId = queuePort.poll();
        if (adId.isEmpty()) {
            rebuildQueue();
            adId = queuePort.poll();
        }
        if (adId.isEmpty()) {
            return Optional.empty();
        }

        return adRepository.findById(adId.get())
                .filter(this::isActive)
                .map(ad -> {
                    queuePort.offer(ad.getId());
                    return AdInfo.from(ad);
                });
    }

    private boolean isActive(Ad ad) {
        return ad.getStatus() == AdStatus.ACTIVE;
    }

    private AdInfo nextAdFromDb() {
        return adRepository.findRandomActive()
                .map(AdInfo::from)
                .orElseThrow(NoActiveAdException::new);
    }

    private void rebuildQueue() {
        if (!queuePort.tryRebuildLock()) {
            return; // 다른 서버가 재구성 중 — nextAdFromQueue()가 빈 Optional 반환하여 DB fallback
        }
        try {
            adRepository.findAllActiveIds().forEach(queuePort::offer);
        } finally {
            queuePort.releaseRebuildLock();
        }
    }
}
