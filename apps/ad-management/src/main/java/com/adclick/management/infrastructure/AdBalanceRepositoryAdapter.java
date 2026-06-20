package com.adclick.management.infrastructure;

import com.adclick.management.domain.AdBalance;
import com.adclick.management.domain.AdBalanceRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AdBalanceRepositoryAdapter implements AdBalanceRepository {

    private final AdBalanceJpaRepository jpaRepository;

    public AdBalanceRepositoryAdapter(AdBalanceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AdBalance save(AdBalance balance) {
        return jpaRepository.save(balance);
    }

    @Override
    public Optional<AdBalance> findByAdId(Long adId) {
        return jpaRepository.findById(adId);
    }

    @Override
    public Optional<AdBalance> findByAdIdForUpdate(Long adId) {
        return jpaRepository.findByAdIdForUpdate(adId);
    }
}
