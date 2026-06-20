package com.adclick.management.infrastructure;

import com.adclick.management.domain.AdBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdBalanceJpaRepository extends JpaRepository<AdBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AdBalance b where b.adId = :adId")
    Optional<AdBalance> findByAdIdForUpdate(@Param("adId") Long adId);
}
