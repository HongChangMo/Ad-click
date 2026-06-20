package com.adclick.click.domain;

import java.util.Optional;

public interface AbuseGuardPort {

    Optional<InvalidClickReason> checkAndMark(Long adId, String ipAddress, String anonymousId);
}
