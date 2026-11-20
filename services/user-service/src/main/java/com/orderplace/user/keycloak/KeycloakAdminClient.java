package com.orderplace.user.keycloak;

import com.orderplace.common.web.ApiException;
import com.orderplace.user.model.UserRole;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Talks to the Keycloak Admin REST API with the service account of client orderplace-user-service
 * (client credentials). Keycloak owns passwords, tokens and sessions; this class only manages accounts.
 */
@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final long EMAIL_LINK_LIFESPAN_SECONDS = 86_400;
    private static final String USER_NOT_FOUND = "User not found in the identity service";

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(@Value("${keycloak.base-url}") String baseUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** Creates an enabled account (username = email) with org_id and org_type attributes. Returns the Keycloak user id. */
    public UUID createUser(String email, String firstName, String lastName, UserRole role, UUID orgId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", email);
        body.put("email", email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("enabled", true);
        body.put("emailVerified", true);
        body.put("attributes", Map.of("org_id", List.of(orgId.toString()), "org_type", List.of(role.name())));

        ResponseEntity<Void> response = call(null, () -> http.post()
                .uri("/admin/realms/{realm}/users", realm)
                .headers(auth()).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity());

        URI location = response.getHeaders().getLocation();
        if (location == null) {
            throw unavailable("The identity service did not return the new user id");
        }
        String path = location.getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    public void assignRole(UUID userId, UserRole role) {
        Map<String, Object> roleRepresentation = call(null, () -> http.get()
                .uri("/admin/realms/{realm}/roles/{name}", realm, role.name())
                .headers(auth()).retrieve().body(MAP));
        call(USER_NOT_FOUND, () -> http.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .headers(auth()).contentType(MediaType.APPLICATION_JSON).body(List.of(roleRepresentation))
                .retrieve().toBodilessEntity());
    }

    /** Sends the "set your password" email (also used for password reset). Fails loudly if Keycloak cannot send it. */
    public void sendUpdatePasswordEmail(UUID userId) {
        call(USER_NOT_FOUND, () -> http.put()
                .uri("/admin/realms/{realm}/users/{id}/execute-actions-email?lifespan={lifespan}",
                        realm, userId, EMAIL_LINK_LIFESPAN_SECONDS)
                .headers(auth()).contentType(MediaType.APPLICATION_JSON).body(List.of("UPDATE_PASSWORD"))
                .retrieve().toBodilessEntity());
    }

    /** Enables or disables the account. Disabling also ends all of the user's sessions. */
    public void setEnabled(UUID userId, boolean enabled) {
        Map<String, Object> representation = new LinkedHashMap<>(getUser(userId));
        if (!Boolean.valueOf(enabled).equals(representation.get("enabled"))) {
            representation.put("enabled", enabled);
            putUser(userId, representation);
        }
        if (!enabled) {
            logout(userId);
        }
    }

    /** Updates name and organisation attributes, then ends sessions so new tokens carry the new org_id. */
    public void updateProfile(UUID userId, String firstName, String lastName, UserRole role, UUID orgId) {
        Map<String, Object> representation = new LinkedHashMap<>(getUser(userId));
        representation.put("firstName", firstName);
        representation.put("lastName", lastName);
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (representation.get("attributes") instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> attributes.put(String.valueOf(k), v));
        }
        attributes.put("org_id", List.of(orgId.toString()));
        attributes.put("org_type", List.of(role.name()));
        representation.put("attributes", attributes);
        putUser(userId, representation);
        logout(userId);
    }

    /** Used only to undo a half-finished create. Never throws: a failure is logged for manual clean-up. */
    public void deleteUserQuietly(UUID userId) {
        try {
            call(USER_NOT_FOUND, () -> http.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                    .headers(auth()).retrieve().toBodilessEntity());
        } catch (RuntimeException e) {
            log.error("CLEAN-UP NEEDED: could not delete Keycloak user {} after a failed create. Remove it manually.",
                    userId, e);
        }
    }

    // ---------------------------------------------------------------- internals

    private Map<String, Object> getUser(UUID userId) {
        return call(USER_NOT_FOUND, () -> http.get()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .headers(auth()).retrieve().body(MAP));
    }

    private void putUser(UUID userId, Map<String, Object> representation) {
        call(USER_NOT_FOUND, () -> http.put()
                .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                .headers(auth()).contentType(MediaType.APPLICATION_JSON).body(representation)
                .retrieve().toBodilessEntity());
    }

    private void logout(UUID userId) {
        call(USER_NOT_FOUND, () -> http.post()
                .uri("/admin/realms/{realm}/users/{id}/logout", realm, userId)
                .headers(auth()).retrieve().toBodilessEntity());
    }

    private Consumer<HttpHeaders> auth() {
        return headers -> headers.setBearerAuth(accessToken());
    }

    private synchronized String accessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> response = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(MAP);
        if (response == null || response.get("access_token") == null) {
            throw unavailable("The identity service did not return an access token");
        }
        cachedToken = String.valueOf(response.get("access_token"));
        long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 30, 5));
        return cachedToken;
    }

    /**
     * Runs a Keycloak call and converts HTTP failures into ApiException.
     * @param notFoundMessage message for a 404, or null if a 404 should be treated as "service misconfigured"
     */
    private <T> T call(String notFoundMessage, Supplier<T> action) {
        try {
            return action.get();
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.conflict("DUPLICATE_RESOURCE", "An account with this email already exists in the identity service");
        } catch (HttpClientErrorException.NotFound e) {
            if (notFoundMessage != null) {
                throw ApiException.notFound(notFoundMessage);
            }
            log.error("Keycloak returned 404: {}", e.getMessage());
            throw unavailable("The identity service is not configured as expected");
        } catch (RestClientException e) {
            log.error("Keycloak call failed: {}", e.getMessage());
            throw unavailable("The identity service is unavailable or rejected the request");
        }
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", message);
    }
}
