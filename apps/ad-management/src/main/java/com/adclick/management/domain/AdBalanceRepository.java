package com.adclick.management.domain;

import java.util.Optional;

public interface AdBalanceRepository {
    AdBalance save(AdBalance balance);
    Optional<AdBalance> findByAdId(Long adId);
}
