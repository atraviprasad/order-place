package com.orderplace.common.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Maps Keycloak realm_access.roles to ROLE_* authorities so @PreAuthorize("hasRole('DEALER')") works. */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = List.of();
        if (jwt.getClaim("realm_access") instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> roles) {
            authorities = roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
