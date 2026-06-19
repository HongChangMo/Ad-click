package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import org.springframework.stereotype.Repository;

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
}
