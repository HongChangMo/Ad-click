package com.adclick.management.domain;

import java.util.Optional;

public interface AdRotationQueuePort {
    void offer(Long adId);
    Optional<Long> poll();
    boolean tryRebuildLock();
    void releaseRebuildLock();
}
