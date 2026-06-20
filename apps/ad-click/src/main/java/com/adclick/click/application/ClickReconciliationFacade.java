package com.adclick.click.application;

import com.adclick.click.application.info.ReconciliationInfo;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.click.domain.InvalidClickReason;
import com.adclick.management.application.BalanceFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClickReconciliationFacade {

    private static final BigDecimal CLICK_REFUND_AMOUNT = BigDecimal.valueOf(50);

    private final ClickEventRepository clickEventRepository;
    private final BalanceFacade balanceFacade;

    public ClickReconciliationFacade(ClickEventRepository clickEventRepository,
                                     BalanceFacade balanceFacade) {
        this.clickEventRepository = clickEventRepository;
        this.balanceFacade = balanceFacade;
    }

    @Transactional
    public ReconciliationInfo reconcile(LocalDateTime from, LocalDateTime to) {
        List<ClickEvent> validEvents = clickEventRepository.findValidEventsBetween(from, to);
        Set<ClickKey> seen = new HashSet<>();
        List<ClickEvent> duplicates = new ArrayList<>();

        for (ClickEvent event : validEvents) {
            ClickKey key = new ClickKey(event.getAdId(), event.getIpAddress());
            if (seen.add(key)) {
                continue;
            }
            event.markInvalid(InvalidClickReason.DUPLICATE_IP);
            duplicates.add(event);
            balanceFacade.refund(event.getAdId(), CLICK_REFUND_AMOUNT);
        }

        clickEventRepository.saveAll(duplicates);
        return new ReconciliationInfo(
                from,
                to,
                duplicates.size(),
                CLICK_REFUND_AMOUNT.multiply(BigDecimal.valueOf(duplicates.size())));
    }

    private record ClickKey(Long adId, String ipAddress) {
    }
}
