package com.orderplace.catalog.service;

import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.catalog.model.Product;
import com.orderplace.catalog.model.ProductImage;
import com.orderplace.catalog.repository.ProductImageRepository;
import com.orderplace.catalog.repository.ProductRepository;
import com.orderplace.catalog.storage.ImageStorage;
import com.orderplace.catalog.storage.ImageType;
import com.orderplace.common.web.ApiException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Image rules: JPEG/PNG/WebP only (checked on file content), 5 MB max (also enforced by multipart settings),
 * limited number per product. The object is written to MinIO first and removed again if the database step fails.
 */
@Service
@Transactional
public class ImageService {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageStorage storage;
    private final ProductViewBuilder builder;
    private final CatalogCache cache;

    @Value("${catalog.max-images-per-product:8}")
    private int maxImages = 8;

    public ImageService(ProductRepository products, ProductImageRepository images, ImageStorage storage,
                        ProductViewBuilder builder, CatalogCache cache) {
        this.products = products;
        this.images = images;
        this.storage = storage;
        this.builder = builder;
        this.cache = cache;
    }

    public ProductResponse upload(UUID productId, byte[] data) {
        Product product = findProduct(productId);
        if (data == null || data.length == 0) {
            throw ApiException.badRequest("The file is empty");
        }
        if (data.length > MAX_BYTES) {
            throw ApiException.unprocessable("IMAGE_TOO_LARGE", "Images can be at most 5 MB");
        }
        ImageType type = ImageType.detect(data).orElseThrow(() ->
                ApiException.unprocessable("UNSUPPORTED_IMAGE_TYPE", "Only JPEG, PNG and WebP images are accepted"));
        long existing = images.countByProductId(productId);
        if (existing >= maxImages) {
            throw ApiException.unprocessable("IMAGE_LIMIT_REACHED", "A product can have at most " + maxImages + " images");
        }

        String key = "products/" + productId + "/" + UUID.randomUUID() + "." + type.extension();
        storage.put(key, data, type.mime());
        try {
            images.saveAndFlush(ProductImage.create(productId, key, type.mime(), existing == 0,
                    images.maxSortOrder(productId) + 1));
        } catch (RuntimeException e) {
            storage.deleteQuietly(key);
            throw e;
        }
        cache.evictProduct(productId);
        return builder.build(product);
    }

    public ProductResponse setPrimary(UUID productId, UUID imageId) {
        Product product = findProduct(productId);
        ProductImage target = images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> ApiException.notFound("Image not found: " + imageId));
        if (!target.isPrimaryImage()) {
            // only one primary per product is allowed by a unique index, so release the old one first
            images.findByProductIdAndPrimaryImageTrue(productId).ifPresent(old -> {
                old.markPrimary(false);
                images.saveAndFlush(old);
            });
            target.markPrimary(true);
            images.saveAndFlush(target);
            cache.evictProduct(productId);
        }
        return builder.build(product);
    }

    public void delete(UUID productId, UUID imageId) {
        findProduct(productId);
        ProductImage image = images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> ApiException.notFound("Image not found: " + imageId));
        boolean wasPrimary = image.isPrimaryImage();
        String key = image.getStorageKey();
        images.delete(image);
        images.flush();
        if (wasPrimary) {
            images.findFirstByProductIdOrderBySortOrderAscCreatedAtAsc(productId).ifPresent(next -> {
                next.markPrimary(true);
                images.saveAndFlush(next);
            });
        }
        cache.evictProduct(productId);
        // the file is removed only after the database change is safe
        AfterCommit.run(() -> storage.deleteQuietly(key));
    }

    private Product findProduct(UUID id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Product not found: " + id));
    }
}
