package com.adclick.management.domain;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(Long adId) {
        super("Insufficient balance for ad: " + adId);
    }
}
