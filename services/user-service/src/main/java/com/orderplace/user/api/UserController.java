package com.orderplace.user.api;

import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.user.api.dto.CreateUserRequest;
import com.orderplace.user.api.dto.MeResponse;
import com.orderplace.user.api.dto.UpdateUserRequest;
import com.orderplace.user.api.dto.UserCreatedResponse;
import com.orderplace.user.api.dto.UserResponse;
import com.orderplace.user.model.UserRole;
import com.orderplace.user.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Served under /api/v1 (server.servlet.context-path). */
@RestController
@RequestMapping("/users")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("email", "firstName", "lastName", "createdAt");

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** Identity from the JWT. Any logged-in user, including Admins. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(jwt);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserCreatedResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserCreatedResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.user().id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastName,asc") String sort) {
        return service.list(q, role, active, PageParams.of(page, size, sort, SORTABLE));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    /** Sends the Keycloak "set your password" email again. */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id) {
        service.resetPassword(id);
        return ResponseEntity.accepted().build();
    }
}
