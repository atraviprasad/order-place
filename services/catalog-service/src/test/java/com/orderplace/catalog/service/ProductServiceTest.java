package com.orderplace.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderplace.catalog.api.dto.CreateProductRequest;
import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.catalog.api.dto.UpdateProductRequest;
import com.orderplace.catalog.model.Availability;
import com.orderplace.catalog.model.Category;
import com.orderplace.catalog.model.PriceHistory;
import com.orderplace.catalog.model.Product;
import com.orderplace.catalog.mapper.ProductMapper;
import com.orderplace.catalog.repository.CategoryRepository;
import com.orderplace.catalog.repository.PriceHistoryRepository;
import com.orderplace.catalog.repository.ProductImageRepository;
import com.orderplace.catalog.repository.ProductRepository;
import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    @Mock ProductImageRepository images;
    @Mock PriceHistoryRepository priceHistory;
    @Mock ProductReader reader;
    @Mock ProductViewBuilder builder;
    @Mock ProductMapper mapper;
    @Mock CategoryService categoryService;
    @Mock CatalogCache cache;
    @InjectMocks ProductService service;

    private final UUID categoryId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private static Category category(boolean active) {
        Category c = Category.create(null, "Seating", 0);
        if (!active) c.deactivate();
        return c;
    }

    private Product product(String price) {
        return Product.create("CHR-1", "Chair", null, categoryId, new BigDecimal(price), "EACH", Availability.AVAILABLE, 1, "{}");
    }

    private CreateProductRequest createRequest(String sku) {
        return new CreateProductRequest(sku, " Chair ", "  ", categoryId, new BigDecimal("4500.00"), null, null, null, null, null);
    }

    private UpdateProductRequest updateRequest(String price, UUID category, Long version) {
        return new UpdateProductRequest("Chair", null, category, new BigDecimal(price), null, null, null, null, version);
    }

    private static ProductResponse response(boolean active) {
        return new ProductResponse(UUID.randomUUID(), "CHR-1", "Chair", null, UUID.randomUUID(), "Seating",
                new BigDecimal("100.00"), "INR", "EACH", Availability.AVAILABLE, 1, Map.of(), active, 0L,
                List.of(), Instant.now(), Instant.now());
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void create_normalisesSkuAndAppliesDefaults() {
        when(categories.findById(categoryId)).thenReturn(Optional.of(category(true)));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(createRequest(" chr-erg-001 "));

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products).saveAndFlush(saved.capture());
        Product p = saved.getValue();
        assertThat(p.getSku()).isEqualTo("CHR-ERG-001");
        assertThat(p.getName()).isEqualTo("Chair");
        assertThat(p.getDescription()).isNull();
        assertThat(p.getCurrency()).isEqualTo("INR");
        assertThat(p.getUnitOfMeasure()).isEqualTo("EACH");
        assertThat(p.getAvailability()).isEqualTo(Availability.AVAILABLE);
        assertThat(p.getMinOrderQty()).isEqualTo(1);
        assertThat(p.getAttributes()).isEqualTo("{}");
        assertThat(p.isActive()).isTrue();
    }

    @Test
    void create_duplicateSku_isRejected() {
        when(products.existsBySkuIgnoreCase("CHR-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("chr-1"))).satisfies(t -> assertCode(t, "DUPLICATE_RESOURCE"));
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void create_unknownCategory_isRejected() {
        when(categories.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest("CHR-1"))).satisfies(t -> assertCode(t, "CATEGORY_NOT_FOUND"));
    }

    @Test
    void create_inactiveCategory_isRejected() {
        when(categories.findById(categoryId)).thenReturn(Optional.of(category(false)));

        assertThatThrownBy(() -> service.create(createRequest("CHR-1"))).satisfies(t -> assertCode(t, "CATEGORY_INACTIVE"));
    }

    @Test
    void update_priceChange_writesHistoryAndEvictsCache() {
        when(products.findById(productId)).thenReturn(Optional.of(product("100.00")));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(productId, updateRequest("120.00", categoryId, null));

        ArgumentCaptor<PriceHistory> history = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistory).save(history.capture());
        assertThat(history.getValue().getOldPrice()).isEqualByComparingTo("100.00");
        assertThat(history.getValue().getNewPrice()).isEqualByComparingTo("120.00");
        assertThat(history.getValue().getProductId()).isEqualTo(productId);
        verify(cache).evictProduct(productId);
    }

    @Test
    void update_samePrice_writesNoHistory() {
        when(products.findById(productId)).thenReturn(Optional.of(product("100.00")));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(productId, updateRequest("100", categoryId, null));

        verify(priceHistory, never()).save(any());
    }

    @Test
    void update_staleVersion_isRejected() {
        Product p = product("100.00");
        ReflectionTestUtils.setField(p, "version", 5L);
        when(products.findById(productId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.update(productId, updateRequest("100", categoryId, 4L)))
                .satisfies(t -> assertCode(t, "VERSION_CONFLICT"));
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void update_moveToInactiveCategory_isRejected() {
        UUID other = UUID.randomUUID();
        when(products.findById(productId)).thenReturn(Optional.of(product("100.00")));
        when(categories.findById(other)).thenReturn(Optional.of(category(false)));

        assertThatThrownBy(() -> service.update(productId, updateRequest("100", other, null)))
                .satisfies(t -> assertCode(t, "CATEGORY_INACTIVE"));
    }

    @Test
    void activate_whenCategoryIsInactive_isRejected() {
        Product p = product("100.00");
        p.deactivate();
        when(products.findById(productId)).thenReturn(Optional.of(p));
        when(categories.findById(categoryId)).thenReturn(Optional.of(category(false)));

        assertThatThrownBy(() -> service.activate(productId)).satisfies(t -> assertCode(t, "CATEGORY_INACTIVE"));
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void deactivate_hidesProductAndEvictsCache() {
        Product p = product("100.00");
        when(products.findById(productId)).thenReturn(Optional.of(p));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivate(productId);

        assertThat(p.isActive()).isFalse();
        verify(cache).evictProduct(productId);
    }

    @Test
    void get_inactiveProduct_isHiddenFromNonAdmins() {
        when(reader.load(productId)).thenReturn(response(false));

        assertThatThrownBy(() -> service.get(productId, false)).satisfies(t -> assertCode(t, "NOT_FOUND"));
        assertThat(service.get(productId, true).active()).isFalse();
    }

    @Test
    void get_activeProduct_isVisibleToEveryone() {
        when(reader.load(productId)).thenReturn(response(true));

        assertThat(service.get(productId, false).active()).isTrue();
    }

    @Test
    void list_minPriceAboveMaxPrice_isRejected() {
        var filter = new ProductService.Filter(null, null, new BigDecimal("500"), new BigDecimal("100"), null, false);

        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 20)))
                .satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }
}
