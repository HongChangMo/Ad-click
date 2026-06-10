package com.adclick.management.application.info;

import com.adclick.management.domain.AdBalance;

import java.math.BigDecimal;

public record BalanceInfo(Long adId, BigDecimal balance) {
    public static BalanceInfo from(AdBalance adBalance) {
        return new BalanceInfo(adBalance.getAdId(), adBalance.getBalance());
    }
}
