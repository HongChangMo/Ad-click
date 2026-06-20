package com.adclick.click.interfaces.api;

import com.adclick.click.infrastructure.outbox.ClickEventOutbox;
import com.adclick.click.infrastructure.outbox.ClickEventOutboxAdminService;
import com.adclick.click.interfaces.api.dto.ClickEventOutboxResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/click-event-outbox")
public class ClickEventOutboxAdminController {

    private final ClickEventOutboxAdminService adminService;

    public ClickEventOutboxAdminController(ClickEventOutboxAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/failed")
    public ResponseEntity<List<ClickEventOutboxResponse>> failed(
            @RequestParam(value = "size", defaultValue = "20") int size) {
        List<ClickEventOutboxResponse> response = adminService.findFailed(size)
                .stream()
                .map(ClickEventOutboxResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{outboxId}/retry")
    public ResponseEntity<ClickEventOutboxResponse> retry(@PathVariable("outboxId") Long outboxId) {
        return adminService.retryFailed(outboxId)
                .map(ClickEventOutboxResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
