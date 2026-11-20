package com.orderplace.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderplace.catalog.api.dto.CategoryNode;
import com.orderplace.catalog.api.dto.CategoryResponse;
import com.orderplace.catalog.api.dto.CreateCategoryRequest;
import com.orderplace.catalog.api.dto.UpdateCategoryRequest;
import com.orderplace.catalog.model.Category;
import com.orderplace.catalog.repository.CategoryRepository;
import com.orderplace.catalog.repository.ProductRepository;
import com.orderplace.common.web.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categories;
    @Mock ProductRepository products;
    @Mock CategoryTreeReader treeReader;
    @Mock CatalogCache cache;
    @InjectMocks CategoryService service;

    private static Category category(UUID id, UUID parentId, String name, boolean active) {
        Category c = Category.create(parentId, name, 0);
        ReflectionTestUtils.setField(c, "id", id);
        if (!active) c.deactivate();
        return c;
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void create_rootCategory_evictsTreeCache() {
        when(categories.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = service.create(new CreateCategoryRequest(" Seating ", null, null));

        assertThat(response.name()).isEqualTo("Seating");
        assertThat(response.sortOrder()).isZero();
        verify(cache).evictCategoryTree();
    }

    @Test
    void create_underInactiveParent_isRejected() {
        UUID parent = UUID.randomUUID();
        when(categories.findById(parent)).thenReturn(Optional.of(category(parent, null, "Old", false)));

        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Chairs", parent, 1)))
                .satisfies(t -> assertCode(t, "PARENT_INACTIVE"));
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void create_unknownParent_isRejected() {
        UUID parent = UUID.randomUUID();
        when(categories.findById(parent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Chairs", parent, 1)))
                .satisfies(t -> assertCode(t, "PARENT_NOT_FOUND"));
    }

    @Test
    void create_duplicateSiblingName_isRejected() {
        when(categories.existsByParentIdIsNullAndNameIgnoreCase("Seating")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCategoryRequest("Seating", null, null)))
                .satisfies(t -> assertCode(t, "DUPLICATE_RESOURCE"));
    }

    @Test
    void update_movingUnderOwnChild_isRejectedAsCycle() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Category catA = category(a, null, "A", true);
        Category catB = category(b, a, "B", true);
        when(categories.findById(a)).thenReturn(Optional.of(catA));
        when(categories.findById(b)).thenReturn(Optional.of(catB));
        when(categories.findAll()).thenReturn(List.of(catA, catB));

        assertThatThrownBy(() -> service.update(a, new UpdateCategoryRequest("A", b, null, null)))
                .satisfies(t -> assertCode(t, "CATEGORY_CYCLE"));
    }

    @Test
    void update_staleVersion_isRejected() {
        UUID a = UUID.randomUUID();
        Category catA = category(a, null, "A", true);
        ReflectionTestUtils.setField(catA, "version", 4L);
        when(categories.findById(a)).thenReturn(Optional.of(catA));

        assertThatThrownBy(() -> service.update(a, new UpdateCategoryRequest("A", null, null, 3L)))
                .satisfies(t -> assertCode(t, "VERSION_CONFLICT"));
    }

    @Test
    void update_rename_evictsTreeAndProductCaches() {
        UUID a = UUID.randomUUID();
        Category catA = category(a, null, "A", true);
        when(categories.findById(a)).thenReturn(Optional.of(catA));
        when(categories.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = service.update(a, new UpdateCategoryRequest("Renamed", null, 5, null));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.sortOrder()).isEqualTo(5);
        verify(cache).evictCategoryTree();
        verify(cache).evictAllProducts();
    }

    @Test
    void deactivate_withActiveChildren_isRejected() {
        UUID a = UUID.randomUUID();
        when(categories.findById(a)).thenReturn(Optional.of(category(a, null, "A", true)));
        when(categories.existsByParentIdAndActiveTrue(a)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(a)).satisfies(t -> assertCode(t, "CATEGORY_NOT_EMPTY"));
    }

    @Test
    void deactivate_withActiveProducts_isRejected() {
        UUID a = UUID.randomUUID();
        when(categories.findById(a)).thenReturn(Optional.of(category(a, null, "A", true)));
        when(products.existsByCategoryIdAndActiveTrue(a)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(a)).satisfies(t -> assertCode(t, "CATEGORY_NOT_EMPTY"));
    }

    @Test
    void deactivate_emptyCategory_works() {
        UUID a = UUID.randomUUID();
        when(categories.findById(a)).thenReturn(Optional.of(category(a, null, "A", true)));
        when(categories.saveAndFlush(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = service.deactivate(a);

        assertThat(response.active()).isFalse();
        verify(cache).evictCategoryTree();
    }

    @Test
    void activate_underInactiveParent_isRejected() {
        UUID a = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        when(categories.findById(a)).thenReturn(Optional.of(category(a, parent, "A", false)));
        when(categories.findById(parent)).thenReturn(Optional.of(category(parent, null, "P", false)));

        assertThatThrownBy(() -> service.activate(a)).satisfies(t -> assertCode(t, "PARENT_INACTIVE"));
    }

    @Test
    void tree_forNonAdmin_hidesInactiveBranches() {
        CategoryNode hiddenChild = new CategoryNode(UUID.randomUUID(), "Hidden child", 0, true, new ArrayList<>());
        CategoryNode inactive = new CategoryNode(UUID.randomUUID(), "Old", 0, false, new ArrayList<>(List.of(hiddenChild)));
        CategoryNode visibleChild = new CategoryNode(UUID.randomUUID(), "Chairs", 0, true, new ArrayList<>());
        CategoryNode visibleChildInactive = new CategoryNode(UUID.randomUUID(), "Stools", 0, false, new ArrayList<>());
        CategoryNode seating = new CategoryNode(UUID.randomUUID(), "Seating", 0, true,
                new ArrayList<>(List.of(visibleChild, visibleChildInactive)));
        when(treeReader.fullTree()).thenReturn(new ArrayList<>(List.of(inactive, seating)));

        List<CategoryNode> forCustomer = service.tree(false);

        assertThat(forCustomer).extracting(CategoryNode::name).containsExactly("Seating");
        assertThat(forCustomer.get(0).children()).extracting(CategoryNode::name).containsExactly("Chairs");
        assertThat(service.tree(true)).hasSize(2);
    }

    @Test
    void subtreeIds_includesRootAndAllDescendants() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(categories.findAll()).thenReturn(List.of(
                category(a, null, "A", true), category(b, a, "B", true),
                category(c, b, "C", true), category(other, null, "Other", true)));

        assertThat(service.subtreeIds(a)).containsExactlyInAnyOrder(a, b, c);
    }
}
