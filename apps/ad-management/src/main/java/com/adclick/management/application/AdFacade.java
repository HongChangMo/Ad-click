package com.adclick.management.application;

import com.adclick.management.application.info.AdInfo;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdFacade {

    private final AdRepository adRepository;

    public AdFacade(AdRepository adRepository) {
        this.adRepository = adRepository;
    }

    @Transactional
    public AdInfo register(Long advertiserId, String name) {
        Ad ad = adRepository.save(Ad.of(advertiserId, name));
        return AdInfo.from(ad);
    }

    @Transactional
    public AdInfo changeStatus(Long adId, AdStatus newStatus) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        ad.changeStatus(newStatus);
        return AdInfo.from(ad);
    }

    @Transactional(readOnly = true)
    public AdInfo getAd(Long adId) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        return AdInfo.from(ad);
    }
}
