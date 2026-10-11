package com.suryaprasanth.grocery.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Blocks cross-site request forgery (CSRF).
 *
 * Login uses a session cookie, and browsers send cookies automatically. Without this check,
 * another website could make a visitor's browser post to /api/orders/checkout on their behalf.
 *
 * Every request that CHANGES something (POST, PUT, PATCH, DELETE) must carry the header
 * "X-Requested-With: XMLHttpRequest". Our own pages add it in js/api.js. A plain HTML form on
 * another site cannot set custom headers, and a script on another site that tries is stopped
 * by the browser's same-origin rules, so forged requests get 403.
 */
public class CsrfHeaderInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Requested-With";
    public static final String VALUE = "XMLHttpRequest";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        if (!VALUE.equals(request.getHeader(HEADER))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Request blocked. Please reload the page and try again.");
        }
        return true;
    }
}
