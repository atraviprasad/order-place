package com.orderplace.gateway.api;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** TEMPORARY test endpoint for Part 1. Delete once real services are routed. */
@RestController
public class WhoAmIController {
    @GetMapping("/api/v1/whoami")
    public Map<String, Object> whoami(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", jwt.getSubject());
        out.put("issuer", String.valueOf(jwt.getIssuer()));
        out.put("username", jwt.getClaimAsString("preferred_username"));
        out.put("realm_access", jwt.getClaim("realm_access"));
        out.put("org_id", jwt.getClaimAsString("org_id"));
        out.put("org_type", jwt.getClaimAsString("org_type"));
        return out;
    }
}
