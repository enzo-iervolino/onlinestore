package com.delta.store.product.service;

import com.delta.store.product.dto.ProductCodeLookupDTO;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCode;
import com.delta.store.product.model.ProductVariant;

import java.util.List;
import java.util.Optional;

public interface ProductCodeService {

    void validateProductCodes(Long productId, String primaryCode, String additionalCodesText);

    void validateVariantCodes(Long variantId, String primaryCode, String additionalCodesText);

    int replaceProductCodes(Product product, String additionalCodesText);

    int replaceVariantCodes(ProductVariant variant, String additionalCodesText);

    String formatProductCodes(Long productId);

    String formatVariantCodes(Long variantId);

    List<ProductCode> findProductCodes(Long productId);

    List<ProductCode> findVariantCodes(Long variantId);

    Optional<ProductCodeLookupDTO> lookup(String code);
}
