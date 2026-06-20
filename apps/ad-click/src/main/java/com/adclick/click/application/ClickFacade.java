package com.adclick.click.application;

import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.application.info.ClickStatsInfo;
import com.adclick.click.domain.AbuseGuardPort;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventPublisher;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.click.domain.InvalidClickReason;
import com.adclick.management.application.AdNotFoundException;
import com.adclick.management.application.BalanceFacade;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ClickFacade {

    private static final BigDecimal CLICK_COST = BigDecimal.valueOf(50);
    private static final LocalDateTime DEFAULT_FROM = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime DEFAULT_TO = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final AdRepository adRepository;
    private final BalanceFacade balanceFacade;
    private final ClickEventRepository clickEventRepository;
    private final AbuseGuardPort abuseGuardPort;
    private final ClickEventPublisher clickEventPublisher;

    public ClickFacade(AdRepository adRepository,
                       BalanceFacade balanceFacade,
                       ClickEventRepository clickEventRepository,
                       AbuseGuardPort abuseGuardPort,
                       ClickEventPublisher clickEventPublisher) {
        this.adRepository = adRepository;
        this.balanceFacade = balanceFacade;
        this.clickEventRepository = clickEventRepository;
        this.abuseGuardPort = abuseGuardPort;
        this.clickEventPublisher = clickEventPublisher;
    }

    @Transactional
    public ClickInfo click(Long adId, String ipAddress, String anonymousId) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        if (ad.getStatus() != AdStatus.ACTIVE) {
            throw new AdNotFoundException(adId);
        }
        Optional<InvalidClickReason> invalidReason = abuseGuardPort.checkAndMark(adId, ipAddress, anonymousId);
        if (invalidReason.isPresent()) {
            ClickEvent event = ClickEvent.invalid(adId, ipAddress, anonymousId, invalidReason.get());
            ClickEvent saved = clickEventRepository.save(event);
            clickEventPublisher.publish(saved);
            return ClickInfo.from(saved);
        }

        balanceFacade.deduct(adId, CLICK_COST, TransactionType.CLICK);
        ClickEvent event = ClickEvent.valid(adId, ipAddress, anonymousId);
        ClickEvent saved = clickEventRepository.save(event);
        clickEventPublisher.publish(saved);
        return ClickInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public ClickStatsInfo stats(Long adId, LocalDateTime from, LocalDateTime to) {
        adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));

        LocalDateTime rangeFrom = from == null ? DEFAULT_FROM : from;
        LocalDateTime rangeTo = to == null ? DEFAULT_TO : to;
        long validCount = clickEventRepository.countByAdIdAndValidityBetween(adId, true, rangeFrom, rangeTo);
        long invalidCount = clickEventRepository.countByAdIdAndValidityBetween(adId, false, rangeFrom, rangeTo);
        return new ClickStatsInfo(adId, rangeFrom, rangeTo, validCount, invalidCount);
    }

}
