package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdRotationQueuePort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AdRotationFacade {

    private final AdRepository adRepository;
    private final AdRotationQueuePort queuePort;

    public AdRotationFacade(AdRepository adRepository, AdRotationQueuePort queuePort) {
        this.adRepository = adRepository;
        this.queuePort = queuePort;
    }

    public AdInfo getNextAd() {
        try {
            Long adId = nextAdId();
            return adRepository.findById(adId)
                    .map(AdInfo::from)
                    .orElseThrow(NoActiveAdException::new);
        } catch (NoActiveAdException e) {
            throw e;
        } catch (Exception e) {
            return adRepository.findRandomActive()
                    .map(AdInfo::from)
                    .orElseThrow(NoActiveAdException::new);
        }
    }

    private Long nextAdId() {
        Optional<Long> adId = queuePort.poll();
        if (adId.isEmpty()) {
            rebuildQueue();
            adId = queuePort.poll();
        }
        adId.ifPresent(queuePort::offer);
        return adId.orElseThrow(NoActiveAdException::new);
    }

    private void rebuildQueue() {
        if (!queuePort.tryRebuildLock()) {
            return;
        }
        try {
            adRepository.findAllActiveIds().forEach(queuePort::offer);
        } finally {
            queuePort.releaseRebuildLock();
        }
    }
}
