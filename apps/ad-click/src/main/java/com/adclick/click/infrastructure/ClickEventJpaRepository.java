package com.adclick.click.infrastructure;

import com.adclick.click.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClickEventJpaRepository extends JpaRepository<ClickEvent, Long> {}
