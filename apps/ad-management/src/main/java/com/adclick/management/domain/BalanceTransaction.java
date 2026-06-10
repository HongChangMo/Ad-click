package com.adclick.management.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "balance_transactions")
public class BalanceTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected BalanceTransaction() {}

    public static BalanceTransaction of(Long adId, BigDecimal amount, TransactionType type) {
        BalanceTransaction t = new BalanceTransaction();
        t.adId = adId;
        t.amount = amount;
        t.type = type;
        t.createdAt = LocalDateTime.now();
        return t;
    }

    public Long getId() { return id; }
    public Long getAdId() { return adId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
