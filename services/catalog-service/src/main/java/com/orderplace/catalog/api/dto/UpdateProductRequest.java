package com.orderplace.catalog.api.dto;

import com.orderplace.catalog.model.Availability;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** The SKU and currency cannot be changed. */
public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 5000) String description,
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Pattern(regexp = "^[A-Z][A-Z_]{1,19}$", message = "Upper case letters, e.g. EACH, SET") String unitOfMeasure,
        Availability availability,
        @Min(1) Integer minOrderQty,
        @Size(max = 30) Map<String, Object> attributes,
        Long version) {}
