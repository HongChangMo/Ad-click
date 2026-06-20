package com.adclick.click.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface ClickEventOutboxJpaRepository extends JpaRepository<ClickEventOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ClickEventOutbox> findByStatusOrderByCreatedAtAscIdAsc(ClickEventOutboxStatus status, Pageable pageable);
}
