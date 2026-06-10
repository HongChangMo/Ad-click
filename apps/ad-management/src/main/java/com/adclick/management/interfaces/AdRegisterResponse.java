package com.adclick.management.interfaces;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdStatus;

public record AdRegisterResponse(Long id, String name, AdStatus status) {

    public static AdRegisterResponse from(Ad ad) {
        return new AdRegisterResponse(ad.getId(), ad.getName(), ad.getStatus());
    }
}
