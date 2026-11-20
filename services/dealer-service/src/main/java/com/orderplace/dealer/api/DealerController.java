package com.orderplace.dealer.api;

import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import com.orderplace.dealer.api.dto.CreateDealerRequest;
import com.orderplace.dealer.api.dto.DealerResponse;
import com.orderplace.dealer.api.dto.DealerSummary;
import com.orderplace.dealer.api.dto.UpdateDealerRequest;
import com.orderplace.dealer.service.DealerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/dealers")
public class DealerController {

    private final DealerService service;

    public DealerController(DealerService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DealerResponse> create(@Valid @RequestBody CreateDealerRequest request) {
        DealerResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/dealers/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<DealerResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return service.list(q, active, PageParams.of(page, size, sort));
    }

    /** Dropdown for customers when creating an order. */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public List<DealerSummary> listActive() {
        return service.listActive();
    }

    /** The dealership of the logged-in dealer user (org_id claim in the JWT). */
    @GetMapping("/me")
    @PreAuthorize("hasRole('DEALER')")
    public DealerResponse me(@AuthenticationPrincipal Jwt jwt) {
        TenantContext ctx = TenantContext.from(jwt);
        if (ctx.orgId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No dealership is linked to this user");
        }
        return service.get(ctx.orgId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DealerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DealerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDealerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public DealerResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public DealerResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }
}
