package com.adclick.click.application;

import com.adclick.click.application.info.ReconciliationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ClickReconciliationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClickReconciliationRunner.class);

    private final ClickReconciliationFacade reconciliationFacade;

    public ClickReconciliationRunner(ClickReconciliationFacade reconciliationFacade) {
        this.reconciliationFacade = reconciliationFacade;
    }

    public ReconciliationInfo runWindow(LocalDateTime from, LocalDateTime to) {
        ReconciliationInfo info = reconciliationFacade.reconcile(from, to);
        log.info(
                "click reconciliation completed. from={}, to={}, invalidatedCount={}, refundedAmount={}",
                info.from(),
                info.to(),
                info.invalidatedCount(),
                info.refundedAmount());
        return info;
    }
}
