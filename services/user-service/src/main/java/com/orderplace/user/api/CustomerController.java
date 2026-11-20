package com.orderplace.user.api;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.user.api.dto.CreateCustomerRequest;
import com.orderplace.user.api.dto.CustomerResponse;
import com.orderplace.user.api.dto.UpdateCustomerRequest;
import com.orderplace.user.api.dto.UserResponse;
import com.orderplace.user.service.CustomerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/customers")
public class CustomerController {

    private static final Set<String> SORTABLE = Set.of("name", "customerCode", "createdAt");

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<CustomerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return service.list(q, active, PageParams.of(page, size, sort, SORTABLE));
    }

    /** The organisation of the logged-in customer user (org_id claim in the JWT). */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
        TenantContext ctx = TenantContext.from(jwt);
        if (ctx.orgId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No customer is linked to this user");
        }
        return service.get(ctx.orgId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> users(@PathVariable UUID id) {
        return service.listUsers(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public CustomerResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }
}
