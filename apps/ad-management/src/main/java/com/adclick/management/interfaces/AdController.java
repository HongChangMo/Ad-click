package com.adclick.management.interfaces;

import com.adclick.management.application.AdService;
import com.adclick.management.domain.Ad;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/ads")
public class AdController {

    private final AdService adService;

    public AdController(AdService adService) {
        this.adService = adService;
    }

    @PostMapping
    public ResponseEntity<AdRegisterResponse> register(@RequestBody AdRegisterRequest request) {
        Ad ad = adService.register(request.advertiserId(), request.name());
        return ResponseEntity
                .created(URI.create("/api/v1/ads/" + ad.getId()))
                .body(AdRegisterResponse.from(ad));
    }

    @PatchMapping("/{adId}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable("adId") Long adId,
                                              @RequestBody AdStatusChangeRequest request) {
        adService.changeStatus(adId, request.status());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{adId}")
    public ResponseEntity<AdRegisterResponse> getAd(@PathVariable("adId") Long adId) {
        Ad ad = adService.getAd(adId);
        return ResponseEntity.ok(AdRegisterResponse.from(ad));
    }
}
