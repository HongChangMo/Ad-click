package com.adclick.click.infrastructure;

import com.adclick.click.domain.AbuseGuardPort;
import com.adclick.click.domain.InvalidClickReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ValKeyAbuseGuardAdapter implements AbuseGuardPort {

    private static final Logger log = LoggerFactory.getLogger(ValKeyAbuseGuardAdapter.class);

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public ValKeyAbuseGuardAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${adclick.click.abuse-guard.ttl-seconds:60}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<InvalidClickReason> checkAndMark(Long adId, String ipAddress, String anonymousId) {
        try {
            String ipKey = ipKey(adId, ipAddress);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(ipKey))) {
                return Optional.of(InvalidClickReason.DUPLICATE_IP);
            }

            String anonKey = anonKey(adId, anonymousId);
            if (anonKey != null && Boolean.TRUE.equals(redisTemplate.hasKey(anonKey))) {
                return Optional.of(InvalidClickReason.DUPLICATE_ANON);
            }

            Boolean ipMarked = redisTemplate.opsForValue().setIfAbsent(ipKey, "1", ttl);
            if (!Boolean.TRUE.equals(ipMarked)) {
                return Optional.of(InvalidClickReason.DUPLICATE_IP);
            }

            if (anonKey != null) {
                Boolean anonMarked = redisTemplate.opsForValue().setIfAbsent(anonKey, "1", ttl);
                if (!Boolean.TRUE.equals(anonMarked)) {
                    return Optional.of(InvalidClickReason.DUPLICATE_ANON);
                }
            }

            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("abuse guard skipped due to valkey failure. adId={}", adId, e);
            return Optional.empty();
        }
    }

    private String ipKey(Long adId, String ipAddress) {
        return "abuse:ip:" + ipAddress + ":" + adId;
    }

    private String anonKey(Long adId, String anonymousId) {
        if (anonymousId == null || anonymousId.isBlank()) {
            return null;
        }
        return "abuse:anon:" + anonymousId + ":" + adId;
    }
}
