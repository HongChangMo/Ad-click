package com.adclick.management.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ad_balances")
public class AdBalance {

    @Id
    private Long adId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AdBalance() {}

    public static AdBalance of(Long adId) {
        AdBalance b = new AdBalance();
        b.adId = adId;
        b.balance = BigDecimal.ZERO;
        b.updatedAt = LocalDateTime.now();
        return b;
    }

    public void add(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void subtract(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public Long getAdId() { return adId; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
