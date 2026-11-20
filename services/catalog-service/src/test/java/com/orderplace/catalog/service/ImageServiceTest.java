package com.orderplace.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orderplace.catalog.model.Availability;
import com.orderplace.catalog.model.Product;
import com.orderplace.catalog.model.ProductImage;
import com.orderplace.catalog.repository.ProductImageRepository;
import com.orderplace.catalog.repository.ProductRepository;
import com.orderplace.catalog.storage.ImageStorage;
import com.orderplace.common.web.ApiException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Mock ProductRepository products;
    @Mock ProductImageRepository images;
    @Mock ImageStorage storage;
    @Mock ProductViewBuilder builder;
    @Mock CatalogCache cache;
    @InjectMocks ImageService service;

    private final UUID productId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();

    private Product product() {
        return Product.create("CHR-1", "Chair", null, UUID.randomUUID(), new BigDecimal("100"), "EACH", Availability.AVAILABLE, 1, "{}");
    }

    private static void assertCode(Throwable t, String code) {
        assertThat(t).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void upload_firstImage_becomesPrimary() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.countByProductId(productId)).thenReturn(0L);
        when(images.maxSortOrder(productId)).thenReturn(0);

        service.upload(productId, PNG);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(images).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPrimaryImage()).isTrue();
        assertThat(saved.getValue().getSortOrder()).isEqualTo(1);
        assertThat(saved.getValue().getContentType()).isEqualTo("image/png");
        assertThat(saved.getValue().getStorageKey()).startsWith("products/" + productId + "/").endsWith(".png");
        verify(storage).put(anyString(), any(byte[].class), anyString());
        verify(cache).evictProduct(productId);
    }

    @Test
    void upload_laterImage_isNotPrimary() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.countByProductId(productId)).thenReturn(2L);
        when(images.maxSortOrder(productId)).thenReturn(2);

        service.upload(productId, PNG);

        ArgumentCaptor<ProductImage> saved = ArgumentCaptor.forClass(ProductImage.class);
        verify(images).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isPrimaryImage()).isFalse();
        assertThat(saved.getValue().getSortOrder()).isEqualTo(3);
    }

    @Test
    void upload_notAnImage_isRejectedBeforeStorage() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> service.upload(productId, "just some text, not an image".getBytes()))
                .satisfies(t -> assertCode(t, "UNSUPPORTED_IMAGE_TYPE"));
        verifyNoInteractions(storage);
    }

    @Test
    void upload_emptyFile_isRejected() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> service.upload(productId, new byte[0])).satisfies(t -> assertCode(t, "VALIDATION_ERROR"));
    }

    @Test
    void upload_tooLarge_isRejected() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));

        assertThatThrownBy(() -> service.upload(productId, new byte[ImageService.MAX_BYTES + 1]))
                .satisfies(t -> assertCode(t, "IMAGE_TOO_LARGE"));
    }

    @Test
    void upload_whenLimitReached_isRejected() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.countByProductId(productId)).thenReturn(8L);

        assertThatThrownBy(() -> service.upload(productId, PNG)).satisfies(t -> assertCode(t, "IMAGE_LIMIT_REACHED"));
        verifyNoInteractions(storage);
    }

    @Test
    void upload_unknownProduct_isNotFound() {
        when(products.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(productId, PNG)).satisfies(t -> assertCode(t, "NOT_FOUND"));
    }

    @Test
    void upload_databaseFailure_removesTheStoredObject() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.countByProductId(productId)).thenReturn(0L);
        when(images.maxSortOrder(productId)).thenReturn(0);
        doThrow(new IllegalStateException("db down")).when(images).saveAndFlush(any(ProductImage.class));

        assertThatThrownBy(() -> service.upload(productId, PNG)).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> uploadedKey = ArgumentCaptor.forClass(String.class);
        verify(storage).put(uploadedKey.capture(), any(byte[].class), anyString());
        verify(storage).deleteQuietly(uploadedKey.getValue());
        verify(cache, never()).evictProduct(any());
    }

    @Test
    void setPrimary_releasesOldPrimaryBeforeSettingNewOne() {
        ProductImage oldPrimary = ProductImage.create(productId, "k1", "image/png", true, 1);
        ProductImage target = ProductImage.create(productId, "k2", "image/png", false, 2);
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.findByIdAndProductId(imageId, productId)).thenReturn(Optional.of(target));
        when(images.findByProductIdAndPrimaryImageTrue(productId)).thenReturn(Optional.of(oldPrimary));

        service.setPrimary(productId, imageId);

        InOrder order = inOrder(images);
        order.verify(images).saveAndFlush(oldPrimary);
        order.verify(images).saveAndFlush(target);
        assertThat(oldPrimary.isPrimaryImage()).isFalse();
        assertThat(target.isPrimaryImage()).isTrue();
        verify(cache).evictProduct(productId);
    }

    @Test
    void setPrimary_alreadyPrimary_changesNothing() {
        ProductImage target = ProductImage.create(productId, "k2", "image/png", true, 1);
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.findByIdAndProductId(imageId, productId)).thenReturn(Optional.of(target));

        service.setPrimary(productId, imageId);

        verify(images, never()).saveAndFlush(any());
        verify(cache, never()).evictProduct(any());
    }

    @Test
    void delete_primaryImage_promotesNextAndRemovesFile() {
        ProductImage primary = ProductImage.create(productId, "products/x/one.png", "image/png", true, 1);
        ProductImage next = ProductImage.create(productId, "products/x/two.png", "image/png", false, 2);
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.findByIdAndProductId(imageId, productId)).thenReturn(Optional.of(primary));
        when(images.findFirstByProductIdOrderBySortOrderAscCreatedAtAsc(productId)).thenReturn(Optional.of(next));

        service.delete(productId, imageId);

        InOrder order = inOrder(images);
        order.verify(images).delete(primary);
        order.verify(images).flush();
        order.verify(images).saveAndFlush(next);
        assertThat(next.isPrimaryImage()).isTrue();
        verify(storage).deleteQuietly("products/x/one.png");
        verify(cache).evictProduct(productId);
    }

    @Test
    void delete_nonPrimaryImage_doesNotPromoteAnything() {
        ProductImage other = ProductImage.create(productId, "products/x/two.png", "image/png", false, 2);
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.findByIdAndProductId(imageId, productId)).thenReturn(Optional.of(other));

        service.delete(productId, imageId);

        verify(images, never()).findFirstByProductIdOrderBySortOrderAscCreatedAtAsc(any());
        verify(storage).deleteQuietly("products/x/two.png");
    }

    @Test
    void delete_unknownImage_isNotFound() {
        when(products.findById(productId)).thenReturn(Optional.of(product()));
        when(images.findByIdAndProductId(imageId, productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(productId, imageId)).satisfies(t -> assertCode(t, "NOT_FOUND"));
        verifyNoInteractions(storage);
    }
}
