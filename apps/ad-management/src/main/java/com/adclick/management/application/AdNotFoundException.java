package com.adclick.management.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AdNotFoundException extends RuntimeException {
    public AdNotFoundException(Long adId) {
        super("Ad not found: " + adId);
    }
}
