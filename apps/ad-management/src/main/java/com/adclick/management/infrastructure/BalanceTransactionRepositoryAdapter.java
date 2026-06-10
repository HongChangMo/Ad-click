package com.adclick.management.infrastructure;

import com.adclick.management.domain.BalanceTransaction;
import com.adclick.management.domain.BalanceTransactionRepository;
import org.springframework.stereotype.Repository;

@Repository
public class BalanceTransactionRepositoryAdapter implements BalanceTransactionRepository {

    private final BalanceTransactionJpaRepository jpaRepository;

    public BalanceTransactionRepositoryAdapter(BalanceTransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public BalanceTransaction save(BalanceTransaction transaction) {
        return jpaRepository.save(transaction);
    }
}
