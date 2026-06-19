package com.adclick.click.interfaces.api;

import com.adclick.click.application.ClickFacade;
import com.adclick.click.application.info.ClickInfo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ads")
public class ClickController {

    private final ClickFacade clickFacade;

    public ClickController(ClickFacade clickFacade) {
        this.clickFacade = clickFacade;
    }

    @PostMapping("/{adId}/clicks")
    public ResponseEntity<ClickInfo> click(
            @PathVariable("adId") Long adId,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ip = extractClientIp(request);
        String anonId = resolveAnonymousId(request, response);
        return ResponseEntity.ok(clickFacade.click(adId, ip, anonId));
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
