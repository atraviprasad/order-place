package com.orderplace.order.client;

import com.orderplace.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Shared helpers for calls to other services using "token relay" (the caller's own bearer token is forwarded). */
final class Downstream {
    private Downstream() {}

    static String bearerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "No bearer token available to call another service");
    }

    static ApiException unavailable(String service) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "The " + service + " is unavailable");
    }

    static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
