package com.orderplace.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Caller identity taken from the JWT. Tenant scope must always come from here, never from request parameters. */
public record TenantContext(String userId, String role, String orgType, UUID orgId) {

    public static TenantContext from(Jwt jwt) {
        String org = jwt.getClaimAsString("org_id");
        return new TenantContext(
                jwt.getSubject(),
                extractRole(jwt),
                jwt.getClaimAsString("org_type"),
                org == null || org.isBlank() ? null : UUID.fromString(org));
    }

    @SuppressWarnings("unchecked")
    private static String extractRole(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> roles) {
            for (String r : List.of("ADMIN", "DEALER", "CUSTOMER")) if (roles.contains(r)) return r;
        }
        return null;
    }

    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean isDealer() { return "DEALER".equals(role); }
    public boolean isCustomer() { return "CUSTOMER".equals(role); }
}
