package com.adclick.click.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClickEventOutboxJpaRepository extends JpaRepository<ClickEventOutbox, Long> {

    List<ClickEventOutbox> findTop50ByStatusOrderByCreatedAtAscIdAsc(ClickEventOutboxStatus status);
}
