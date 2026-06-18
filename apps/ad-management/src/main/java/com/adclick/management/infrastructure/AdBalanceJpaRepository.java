package com.adclick.management.infrastructure;

import com.adclick.management.domain.AdBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdBalanceJpaRepository extends JpaRepository<AdBalance, Long> {}
