package com.adclick.management.interfaces.api;

import com.adclick.management.application.BalanceFacade;
import com.adclick.management.application.info.BalanceInfo;
import com.adclick.management.interfaces.api.dto.BalanceChargeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ads")
public class BalanceController {

    private final BalanceFacade balanceFacade;

    public BalanceController(BalanceFacade balanceFacade) {
        this.balanceFacade = balanceFacade;
    }

    @PostMapping("/{adId}/balance/charge")
    public ResponseEntity<BalanceInfo> charge(
            @PathVariable("adId") Long adId,
            @RequestBody BalanceChargeRequest request) {
        return ResponseEntity.ok(balanceFacade.charge(adId, request.amount()));
    }

    @GetMapping("/{adId}/balance")
    public ResponseEntity<BalanceInfo> getBalance(@PathVariable("adId") Long adId) {
        return ResponseEntity.ok(balanceFacade.getBalance(adId));
    }
}
