package com.adclick.click.domain;

public interface ReconciliationLockPort {

    boolean tryLock(String lockKey);

    void release(String lockKey);
}
