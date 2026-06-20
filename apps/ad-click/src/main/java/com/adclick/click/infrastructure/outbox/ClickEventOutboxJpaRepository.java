package com.adclick.click.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClickEventOutboxJpaRepository extends JpaRepository<ClickEventOutbox, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from ClickEventOutbox e
            where e.status = :status
              and e.nextRetryAt <= :now
            order by e.createdAt asc, e.id asc
            """)
    List<ClickEventOutbox> findRetryablePendingForUpdate(
            ClickEventOutboxStatus status,
            LocalDateTime now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from ClickEventOutbox e
            where e.status = :status
              and e.claimedAt < :staleBefore
            order by e.claimedAt asc, e.id asc
            """)
    List<ClickEventOutbox> findStaleProcessingForUpdate(
            ClickEventOutboxStatus status,
            LocalDateTime staleBefore,
            Pageable pageable);

    List<ClickEventOutbox> findByStatusOrderByFailedAtDescIdDesc(
            ClickEventOutboxStatus status,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClickEventOutbox> findByIdAndStatus(Long id, ClickEventOutboxStatus status);
}
