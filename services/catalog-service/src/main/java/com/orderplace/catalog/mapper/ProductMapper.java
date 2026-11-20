package com.orderplace.catalog.mapper;

import com.orderplace.catalog.api.dto.ImageResponse;
import com.orderplace.catalog.api.dto.ProductResponse;
import com.orderplace.catalog.api.dto.ProductSummary;
import com.orderplace.catalog.model.Product;
import com.orderplace.catalog.model.ProductImage;
import com.orderplace.catalog.storage.ImageStorage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    private final ImageStorage storage;

    public ProductMapper(ImageStorage storage) {
        this.storage = storage;
    }

    public ImageResponse toImage(ProductImage i) {
        return new ImageResponse(i.getId(), storage.publicUrl(i.getStorageKey()), i.isPrimaryImage(), i.getSortOrder());
    }

    public ProductResponse toResponse(Product p, String categoryName, List<ProductImage> images) {
        List<ImageResponse> imageDtos = new ArrayList<>();
        for (ProductImage i : images) imageDtos.add(toImage(i));
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getCategoryId(), categoryName,
                p.getUnitPrice(), p.getCurrency(), p.getUnitOfMeasure(), p.getAvailability(), p.getMinOrderQty(),
                AttributesJson.fromJson(p.getAttributes()), p.isActive(), p.getVersion(), imageDtos,
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public ProductSummary toSummary(Product p, ProductImage primary) {
        return new ProductSummary(p.getId(), p.getSku(), p.getName(), p.getCategoryId(), p.getUnitPrice(), p.getCurrency(),
                p.getUnitOfMeasure(), p.getAvailability(), p.getMinOrderQty(), p.isActive(),
                primary == null ? null : storage.publicUrl(primary.getStorageKey()));
    }
}
