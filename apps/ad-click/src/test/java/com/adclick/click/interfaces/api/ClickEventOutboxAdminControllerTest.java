package com.adclick.click.interfaces.api;

import com.adclick.click.infrastructure.outbox.ClickEventOutbox;
import com.adclick.click.infrastructure.outbox.ClickEventOutboxAdminService;
import com.adclick.click.infrastructure.outbox.ClickEventOutboxStatus;
import com.adclick.click.interfaces.api.dto.ClickEventOutboxResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClickEventOutboxAdminControllerTest {

    @Test
    void failed_returns_failed_outbox_rows() {
        ClickEventOutboxAdminService adminService = mock(ClickEventOutboxAdminService.class);
        ClickEventOutboxAdminController controller = new ClickEventOutboxAdminController(adminService);
        ClickEventOutbox event = failedEvent();
        given(adminService.findFailed(20)).willReturn(List.of(event));

        ResponseEntity<List<ClickEventOutboxResponse>> response = controller.failed(20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).status()).isEqualTo(ClickEventOutboxStatus.FAILED);
    }

    @Test
    void retry_returns_404_when_failed_outbox_row_does_not_exist() {
        ClickEventOutboxAdminService adminService = mock(ClickEventOutboxAdminService.class);
        ClickEventOutboxAdminController controller = new ClickEventOutboxAdminController(adminService);
        given(adminService.retryFailed(100L)).willReturn(Optional.empty());

        ResponseEntity<ClickEventOutboxResponse> response = controller.retry(100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ClickEventOutbox failedEvent() {
        ClickEventOutbox event = ClickEventOutbox.pending("ad-click-events", "1", "{}");
        event.markProcessing("relay-1");
        event.markFailed("broker unavailable", LocalDateTime.now(), 1);
        return event;
    }
}
