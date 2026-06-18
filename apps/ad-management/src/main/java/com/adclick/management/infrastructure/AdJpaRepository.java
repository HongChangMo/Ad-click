package com.adclick.management.infrastructure;

import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdJpaRepository extends JpaRepository<Ad, Long> {

    @Query("SELECT a.id FROM Ad a WHERE a.status = :status")
    List<Long> findAllIdsByStatus(@Param("status") AdStatus status);

    @Query(value = "SELECT * FROM ads WHERE status = 'ACTIVE' ORDER BY RAND() LIMIT 1",
           nativeQuery = true)
    Optional<Ad> findRandomActive();
}
