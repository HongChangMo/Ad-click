package com.adclick.management.interfaces.api;

import com.adclick.management.application.AdFacade;
import com.adclick.management.application.info.AdInfo;
import com.adclick.management.interfaces.api.dto.AdRegisterRequest;
import com.adclick.management.interfaces.api.dto.AdStatusChangeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/ads")
public class AdController {

    private final AdFacade adFacade;

    public AdController(AdFacade adFacade) {
        this.adFacade = adFacade;
    }

    @PostMapping
    public ResponseEntity<AdInfo> register(@RequestBody AdRegisterRequest request) {
        AdInfo info = adFacade.register(request.advertiserId(), request.name());
        return ResponseEntity
                .created(URI.create("/api/v1/ads/" + info.id()))
                .body(info);
    }

    @PatchMapping("/{adId}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable("adId") Long adId,
                                              @RequestBody AdStatusChangeRequest request) {
        adFacade.changeStatus(adId, request.status());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{adId}")
    public ResponseEntity<AdInfo> getAd(@PathVariable("adId") Long adId) {
        return ResponseEntity.ok(adFacade.getAd(adId));
    }
}
