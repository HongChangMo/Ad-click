package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdRotationQueuePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AdRotationFacade {

    private final AdRepository adRepository;
    private final AdRotationQueuePort queuePort;

    public AdRotationFacade(AdRepository adRepository, AdRotationQueuePort queuePort) {
        this.adRepository = adRepository;
        this.queuePort = queuePort;
    }

    @Transactional(readOnly = true)
    public AdInfo getNextAd() {
        try {
            return nextAdFromQueue()
                    .orElseGet(this::nextAdFromDb);
        } catch (NoActiveAdException e) {
            throw e;
        } catch (Exception e) {
            // Valkey 장애 Fallback
            return nextAdFromDb();
        }
    }

    private Optional<AdInfo> nextAdFromQueue() {
        Optional<Long> adId = queuePort.poll();
        if (adId.isEmpty()) {
            rebuildQueue();
            adId = queuePort.poll();
        }
        adId.ifPresent(queuePort::offer);
        return adId.flatMap(adRepository::findById).map(AdInfo::from);
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
