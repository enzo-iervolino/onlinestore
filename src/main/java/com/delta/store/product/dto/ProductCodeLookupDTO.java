package com.delta.store.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductCodeLookupDTO(
        String requestedCode,
        String normalizedCode,
        String matchedCode,
        String codeType,
        String ownerType,
        Long productId,
        UUID productPublicId,
        String productCode,
        String productName,
        Long variantId,
        String variantCode,
        BigDecimal price,
        Integer stockQty,
        boolean enabled,
        String image
) {
}
