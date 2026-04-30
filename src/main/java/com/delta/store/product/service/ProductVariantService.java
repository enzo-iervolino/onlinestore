package com.delta.store.product.service;

import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductVariantService {

    int createMissingVariants(Product product,
                              List<String> variantCodes,
                              List<Long> colorIds,
                              List<Long> sizeIds);

    List<ProductVariant> findByProductId(Long productId);

    Optional<ProductVariant> findByIdForEdit(Long variantId);

    ProductVariant createVariant(Long productId,
                                 Long colorId,
                                 Long sizeId,
                                 String variantCode,
                                 BigDecimal price,
                                 Integer stockQty,
                                 boolean enabled,
                                 MultipartFile mainImageFile,
                                 boolean generateMainWebmFromImage,
                                 List<MultipartFile> galleryFiles);

    ProductVariant updateVariant(Long variantId,
                                 String variantCode,
                                 BigDecimal price,
                                 Integer stockQty,
                                 boolean enabled,
                                 MultipartFile mainImageFile,
                                 boolean generateMainWebmFromImage,
                                 List<MultipartFile> galleryFiles,
                                 List<Long> removeImageIds);

    void deleteVariant(Long variantId);
}
