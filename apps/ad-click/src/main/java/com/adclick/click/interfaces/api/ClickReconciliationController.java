package com.adclick.click.interfaces.api;

import com.adclick.click.application.ClickReconciliationRunner;
import com.adclick.click.application.info.ReconciliationInfo;
import com.adclick.click.interfaces.api.dto.ReconciliationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clicks")
public class ClickReconciliationController {

    private final ClickReconciliationRunner reconciliationRunner;

    public ClickReconciliationController(ClickReconciliationRunner reconciliationRunner) {
        this.reconciliationRunner = reconciliationRunner;
    }

    @PostMapping("/reconciliation")
    public ResponseEntity<ReconciliationInfo> reconcile(@RequestBody ReconciliationRequest request) {
        return ResponseEntity.ok(reconciliationRunner.runWindow(request.from(), request.to()));
    }
}
