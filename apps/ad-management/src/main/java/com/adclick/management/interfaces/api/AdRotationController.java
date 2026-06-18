package com.adclick.management.interfaces.api;

import com.adclick.management.application.AdRotationFacade;
import com.adclick.management.application.info.AdInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ads")
public class AdRotationController {

    private final AdRotationFacade adRotationFacade;

    public AdRotationController(AdRotationFacade adRotationFacade) {
        this.adRotationFacade = adRotationFacade;
    }

    @GetMapping("/next")
    public ResponseEntity<AdInfo> getNext() {
        return ResponseEntity.ok(adRotationFacade.getNextAd());
    }
}
