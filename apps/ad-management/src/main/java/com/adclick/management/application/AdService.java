package com.adclick.management.application;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.infrastructure.AdJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdService {

    private final AdJpaRepository adRepository;

    public AdService(AdJpaRepository adRepository) {
        this.adRepository = adRepository;
    }

    @Transactional
    public Ad register(Long advertiserId, String name) {
        return adRepository.save(Ad.of(advertiserId, name));
    }

    @Transactional
    public Ad changeStatus(Long adId, AdStatus newStatus) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        ad.changeStatus(newStatus);
        return ad;
    }

    @Transactional(readOnly = true)
    public Ad getAd(Long adId) {
        return adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
    }
}
