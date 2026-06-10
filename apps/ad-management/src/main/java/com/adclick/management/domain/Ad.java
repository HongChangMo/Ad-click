package com.adclick.management.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ads")
public class Ad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long advertiserId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Ad() {}

    public static Ad of(Long advertiserId, String name) {
        Ad ad = new Ad();
        ad.advertiserId = advertiserId;
        ad.name = name;
        ad.status = AdStatus.ACTIVE;
        ad.createdAt = LocalDateTime.now();
        ad.updatedAt = LocalDateTime.now();
        return ad;
    }

    public void changeStatus(AdStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAdvertiserId() { return advertiserId; }
    public String getName() { return name; }
    public AdStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
