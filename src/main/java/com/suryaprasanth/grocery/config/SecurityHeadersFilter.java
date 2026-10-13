package com.suryaprasanth.grocery.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Browser security headers on every response (pages, API and images).
 *
 * - Content-Security-Policy: scripts, styles, API calls and forms only from this site; photos from this
 *   site (or https for older product links); fonts from Google Fonts; nothing may put this site in a frame
 *   (stops click-jacking of the admin and seller pages). Our pages use inline scripts, so 'unsafe-inline'
 *   is still needed for script-src; every value inserted into a page is escaped (see escapeHtml in api.js).
 * - X-Content-Type-Options: the browser must trust our Content-Type (an uploaded "photo" can't run as a script).
 * - Referrer-Policy: other sites only see our domain, never a page address with an order id in it.
 * - Permissions-Policy: only location (for "distance to shop") is allowed, and only for this site.
 * - Cache-Control no-store on /api: personal data (orders, invoices) is never kept in shared caches.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    public static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data: blob: https:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=()");
        if (request.getRequestURI().startsWith("/api/") && !request.getRequestURI().startsWith("/api/images/")) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
