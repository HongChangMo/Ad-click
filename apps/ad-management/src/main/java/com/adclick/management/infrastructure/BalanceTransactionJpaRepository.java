package com.adclick.management.infrastructure;

import com.adclick.management.domain.BalanceTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BalanceTransactionJpaRepository extends JpaRepository<BalanceTransaction, Long> {}
