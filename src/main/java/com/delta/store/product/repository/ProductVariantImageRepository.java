package com.delta.store.product.repository;

import com.delta.store.product.model.ProductVariantImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantImageRepository extends JpaRepository<ProductVariantImage, Long> {

    List<ProductVariantImage> findByVariantIdOrderBySortOrderAscIdAsc(Long variantId);

    long countByImageUrlAndVariantIdNot(String imageUrl, Long variantId);
}