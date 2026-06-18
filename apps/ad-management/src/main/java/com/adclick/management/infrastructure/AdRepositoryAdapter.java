package com.adclick.management.infrastructure;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AdRepositoryAdapter implements AdRepository {

    private final AdJpaRepository jpaRepository;

    public AdRepositoryAdapter(AdJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Ad save(Ad ad) {
        return jpaRepository.save(ad);
    }

    @Override
    public Optional<Ad> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Long> findAllActiveIds() {
        return jpaRepository.findAllIdsByStatus(AdStatus.ACTIVE);
    }

    @Override
    public Optional<Ad> findRandomActive() {
        return jpaRepository.findRandomActive();
    }
}
