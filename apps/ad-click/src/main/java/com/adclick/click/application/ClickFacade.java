package com.adclick.click.application;

import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.domain.ClickEvent;
import com.adclick.click.domain.ClickEventRepository;
import com.adclick.management.application.AdNotFoundException;
import com.adclick.management.application.BalanceFacade;
import com.adclick.management.domain.Ad;
import com.adclick.management.domain.AdRepository;
import com.adclick.management.domain.AdStatus;
import com.adclick.management.domain.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ClickFacade {

    private static final BigDecimal CLICK_COST = BigDecimal.valueOf(50);

    private final AdRepository adRepository;
    private final BalanceFacade balanceFacade;
    private final ClickEventRepository clickEventRepository;

    public ClickFacade(AdRepository adRepository,
                       BalanceFacade balanceFacade,
                       ClickEventRepository clickEventRepository) {
        this.adRepository = adRepository;
        this.balanceFacade = balanceFacade;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional
    public ClickInfo click(Long adId, String ipAddress, String anonymousId) {
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException(adId));
        if (ad.getStatus() != AdStatus.ACTIVE) {
            throw new AdNotFoundException(adId);
        }
        balanceFacade.deduct(adId, CLICK_COST, TransactionType.CLICK);
        ClickEvent event = ClickEvent.valid(adId, ipAddress, anonymousId);
        return ClickInfo.from(clickEventRepository.save(event));
    }
}
