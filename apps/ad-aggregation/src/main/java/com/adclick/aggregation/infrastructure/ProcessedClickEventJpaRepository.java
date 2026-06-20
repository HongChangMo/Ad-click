package com.adclick.aggregation.infrastructure;

import com.adclick.aggregation.domain.ProcessedClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedClickEventJpaRepository extends JpaRepository<ProcessedClickEvent, Long> {
}
