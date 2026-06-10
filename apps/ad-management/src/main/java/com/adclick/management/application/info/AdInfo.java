package com.adclick.management.application.info;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdStatus;

public record AdInfo(Long id, String name, AdStatus status) {

    public static AdInfo from(Ad ad) {
        return new AdInfo(ad.getId(), ad.getName(), ad.getStatus());
    }
}
