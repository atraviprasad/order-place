package com.orderplace.catalog.service;

import com.orderplace.catalog.api.dto.CreateProductRequest;
import com.orderplace.catalog.api.dto.PriceHistoryResponse;
import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.catalog.api.dto.ProductSummary;
import com.orderplace.catalog.api.dto.UpdateProductRequest;
import com.orderplace.catalog.model.Availability;
import com.orderplace.catalog.model.Category;
import com.orderplace.catalog.model.PriceHistory;
import com.orderplace.catalog.model.Product;
import com.orderplace.catalog.model.ProductImage;
import com.orderplace.catalog.mapper.AttributesJson;
import com.orderplace.catalog.mapper.ProductMapper;
import com.orderplace.catalog.repository.CategoryRepository;
import com.orderplace.catalog.repository.PriceHistoryRepository;
import com.orderplace.catalog.repository.ProductImageRepository;
import com.orderplace.catalog.repository.ProductRepository;
import com.orderplace.common.web.ApiException;
import com.orderplace.common.web.PagedResponse;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final PriceHistoryRepository priceHistory;
    private final ProductReader reader;
    private final ProductViewBuilder builder;
    private final ProductMapper mapper;
    private final CategoryService categoryService;
    private final CatalogCache cache;

    public ProductService(ProductRepository products, CategoryRepository categories, ProductImageRepository images,
                          PriceHistoryRepository priceHistory, ProductReader reader, ProductViewBuilder builder,
                          ProductMapper mapper, CategoryService categoryService, CatalogCache cache) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.priceHistory = priceHistory;
        this.reader = reader;
        this.builder = builder;
        this.mapper = mapper;
        this.categoryService = categoryService;
        this.cache = cache;
    }

    /** Filters for the product list. includeInactive is honoured for admins only (the controller enforces that). */
    public record Filter(String q, UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice,
                         Availability availability, boolean includeInactive) {}

    public PagedResponse<ProductSummary> list(Filter f, Pageable pageable) {
        if (f.minPrice() != null && f.maxPrice() != null && f.minPrice().compareTo(f.maxPrice()) > 0) {
            throw ApiException.badRequest("minPrice cannot be greater than maxPrice");
        }
        Set<UUID> categoryIds = f.categoryId() == null ? null : categoryService.subtreeIds(f.categoryId());
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!f.includeInactive()) ps.add(cb.isTrue(root.get("active")));
            if (f.q() != null && !f.q().isBlank()) {
                String pattern = "%" + escapeLike(f.q().strip().toLowerCase(Locale.ROOT)) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sku")), pattern, '\\')));
            }
            if (categoryIds != null) ps.add(root.get("categoryId").in(categoryIds));
            if (f.minPrice() != null) ps.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("unitPrice"), f.minPrice()));
            if (f.maxPrice() != null) ps.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("unitPrice"), f.maxPrice()));
            if (f.availability() != null) ps.add(cb.equal(root.get("availability"), f.availability()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<Product> page = products.findAll(spec, pageable);

        Map<UUID, ProductImage> primary = new HashMap<>();
        if (!page.isEmpty()) {
            List<UUID> ids = page.getContent().stream().map(Product::getId).toList();
            for (ProductImage i : images.findByProductIdInAndPrimaryImageTrue(ids)) primary.put(i.getProductId(), i);
        }
        List<ProductSummary> rows = page.getContent().stream()
                .map(p -> mapper.toSummary(p, primary.get(p.getId()))).toList();
        return new PagedResponse<>(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Inactive products look like they do not exist to anyone but an admin. */
    public ProductResponse get(UUID id, boolean admin) {
        ProductResponse response = reader.load(id);
        if (!response.active() && !admin) {
            throw ApiException.notFound("Product not found: " + id);
        }
        return response;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest r) {
        String sku = r.sku().strip().toUpperCase(Locale.ROOT);
        if (products.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("DUPLICATE_RESOURCE", "SKU already exists: " + sku);
        }
        requireUsableCategory(r.categoryId());
        Product product = Product.create(sku, r.name().strip(), clean(r.description()), r.categoryId(),
                r.unitPrice(), r.unitOfMeasure() == null ? "EACH" : r.unitOfMeasure(),
                r.availability() == null ? Availability.AVAILABLE : r.availability(),
                r.minOrderQty() == null ? 1 : r.minOrderQty(), AttributesJson.toJson(r.attributes()));
        return builder.build(saveAndFlush(product));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest r) {
        Product product = find(id);
        if (r.version() != null && !r.version().equals(product.getVersion())) {
            throw ApiException.conflict("VERSION_CONFLICT", "This product was changed by someone else. Reload and try again.");
        }
        if (!product.getCategoryId().equals(r.categoryId())) requireUsableCategory(r.categoryId());

        BigDecimal oldPrice = product.getUnitPrice();
        product.update(r.name().strip(), clean(r.description()), r.categoryId(), r.unitPrice(),
                r.unitOfMeasure() == null ? product.getUnitOfMeasure() : r.unitOfMeasure(),
                r.availability() == null ? product.getAvailability() : r.availability(),
                r.minOrderQty() == null ? product.getMinOrderQty() : r.minOrderQty(),
                r.attributes() == null ? product.getAttributes() : AttributesJson.toJson(r.attributes()));
        Product saved = saveAndFlush(product);
        if (oldPrice.compareTo(r.unitPrice()) != 0) {
            priceHistory.save(PriceHistory.of(id, oldPrice, r.unitPrice()));
        }
        cache.evictProduct(id);
        return builder.build(saved);
    }

    @Transactional
    public ProductResponse deactivate(UUID id) {
        Product product = find(id);
        if (product.isActive()) {
            product.deactivate();
            product = saveAndFlush(product);
            cache.evictProduct(id);
        }
        return builder.build(product);
    }

    @Transactional
    public ProductResponse activate(UUID id) {
        Product product = find(id);
        if (!product.isActive()) {
            requireUsableCategory(product.getCategoryId());
            product.activate();
            product = saveAndFlush(product);
            cache.evictProduct(id);
        }
        return builder.build(product);
    }

    public PagedResponse<PriceHistoryResponse> priceHistory(UUID id, Pageable pageable) {
        find(id);
        Page<PriceHistory> page = priceHistory.findByProductIdOrderByChangedAtDesc(id, pageable);
        List<PriceHistoryResponse> rows = page.getContent().stream()
                .map(h -> new PriceHistoryResponse(h.getOldPrice(), h.getNewPrice(), h.getChangedBy(), h.getChangedAt())).toList();
        return new PagedResponse<>(rows, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private void requireUsableCategory(UUID categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> ApiException.unprocessable("CATEGORY_NOT_FOUND", "Category not found: " + categoryId));
        if (!category.isActive()) {
            throw ApiException.unprocessable("CATEGORY_INACTIVE", "The category '" + category.getName() + "' is inactive");
        }
    }

    private Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found: " + id));
    }

    private Product saveAndFlush(Product p) {
        try {
            return products.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DUPLICATE_RESOURCE", "SKU already exists");
        }
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
