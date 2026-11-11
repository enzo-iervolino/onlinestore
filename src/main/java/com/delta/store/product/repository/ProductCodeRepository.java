package com.delta.store.product.repository;

import com.delta.store.product.model.ProductCode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductCodeRepository extends JpaRepository<ProductCode, Long> {

    @EntityGraph(attributePaths = {
            "product",
            "variant",
            "variant.product",
            "variant.color",
            "variant.size"
    })
    Optional<ProductCode> findByNormalizedCode(String normalizedCode);

    List<ProductCode> findByProduct_IdOrderByCodeTypeAscCodeValueAsc(Long productId);

    List<ProductCode> findByVariant_IdOrderByCodeTypeAscCodeValueAsc(Long variantId);

    long countByProduct_Id(Long productId);

    long countByVariant_Id(Long variantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProductCode c where c.product.id = :productId")
    int deleteAllByProductId(@Param("productId") Long productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProductCode c where c.variant.id = :variantId")
    int deleteAllByVariantId(@Param("variantId") Long variantId);
}
