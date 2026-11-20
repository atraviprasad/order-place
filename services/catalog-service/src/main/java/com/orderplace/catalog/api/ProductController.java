package com.orderplace.catalog.api;

import com.orderplace.catalog.api.dto.CreateProductRequest;
import com.orderplace.catalog.api.dto.PriceHistoryResponse;
import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.catalog.api.dto.ProductSummary;
import com.orderplace.catalog.api.dto.UpdateProductRequest;
import com.orderplace.catalog.model.Availability;
import com.orderplace.catalog.service.ImageService;
import com.orderplace.catalog.service.ProductService;
import com.orderplace.common.security.TenantContext;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PageParams;
import com.orderplace.common.web.PagedResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Served under /api/v1 (server.servlet.context-path). */
@RestController
@RequestMapping("/products")
public class ProductController {

    private static final Set<String> SORTABLE = Set.of("name", "sku", "unitPrice", "createdAt");
    private static final String ANY_ROLE = "hasAnyRole('ADMIN','CUSTOMER','DEALER')";

    private final ProductService products;
    private final ImageService images;

    public ProductController(ProductService products, ImageService images) {
        this.products = products;
        this.images = images;
    }

    @GetMapping
    @PreAuthorize(ANY_ROLE)
    public PagedResponse<ProductSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Availability availability,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        boolean admin = TenantContext.from(jwt).isAdmin();
        var filter = new ProductService.Filter(q, categoryId, minPrice, maxPrice, availability, admin && includeInactive);
        return products.list(filter, PageParams.of(page, size, sort, SORTABLE));
    }

    @GetMapping("/{id}")
    @PreAuthorize(ANY_ROLE)
    public ProductResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return products.get(id, TenantContext.from(jwt).isAdmin());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = products.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse deactivate(@PathVariable UUID id) {
        return products.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse activate(@PathVariable UUID id) {
        return products.activate(id);
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The uploaded file could not be read");
        }
        return ResponseEntity.status(201).body(images.upload(id, data));
    }

    @PutMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse setPrimaryImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        return images.setPrimary(id, imageId);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        images.delete(id, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/price-history")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<PriceHistoryResponse> priceHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return products.priceHistory(id, PageParams.of(page, size, "changedAt,desc", Set.of("changedAt")));
    }
}
