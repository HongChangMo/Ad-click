package com.adclick.management.domain;

import java.util.Optional;

public interface AdRepository {
    Ad save(Ad ad);
    Optional<Ad> findById(Long id);
}
