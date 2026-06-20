package com.adclick.click.interfaces.api;

import com.adclick.click.application.ClickFacade;
import com.adclick.click.application.info.ClickInfo;
import com.adclick.click.application.info.ClickStatsInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ads")
public class ClickController {

    private final ClickFacade clickFacade;
    private final ClickRateLimiter rateLimiter;

    public ClickController(ClickFacade clickFacade, ClickRateLimiter rateLimiter) {
        this.clickFacade = clickFacade;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/{adId}/clicks")
    public ResponseEntity<ClickInfo> click(
            @PathVariable("adId") Long adId,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ip = extractClientIp(request);
        if (!rateLimiter.allow(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        String anonId = resolveAnonymousId(request, response);
        return ResponseEntity.ok(clickFacade.click(adId, ip, anonId));
    }

    @GetMapping("/{adId}/clicks/stats")
    public ResponseEntity<ClickStatsInfo> stats(
            @PathVariable("adId") Long adId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(clickFacade.stats(adId, from, to));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveAnonymousId(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("anonymous_id".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String newId = UUID.randomUUID().toString();
        Cookie cookie = new Cookie("anonymous_id", newId);
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return newId;
    }
}
