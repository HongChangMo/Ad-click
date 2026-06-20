package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class ClickEventRepositoryAdapter implements ClickEventRepository {

    private final ClickEventJpaRepository jpaRepository;

    public ClickEventRepositoryAdapter(ClickEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ClickEvent save(ClickEvent event) {
        return jpaRepository.save(event);
    }

    @Override
    public long countByAdIdAndValidityBetween(Long adId, boolean isValid, LocalDateTime from, LocalDateTime to) {
        return jpaRepository.countByAdIdAndIsValidAndClickedAtBetween(adId, isValid, from, to);
    }
}
