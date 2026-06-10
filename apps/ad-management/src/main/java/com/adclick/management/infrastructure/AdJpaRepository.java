package com.adclick.management.infrastructure;

import com.adclick.management.domain.Ad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdJpaRepository extends JpaRepository<Ad, Long> {}
