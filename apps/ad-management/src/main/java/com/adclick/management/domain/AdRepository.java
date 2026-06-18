package com.adclick.management.domain;

import java.util.List;
import java.util.Optional;

public interface AdRepository {
    Ad save(Ad ad);
    Optional<Ad> findById(Long id);
    List<Long> findAllActiveIds();
    Optional<Ad> findRandomActive();
}
