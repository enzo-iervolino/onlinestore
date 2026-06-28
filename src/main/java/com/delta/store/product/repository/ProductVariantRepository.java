package com.delta.store.product.repository;

import com.delta.store.product.model.ColorOption;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;
import com.delta.store.product.model.SizeOption;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    boolean existsByVariantCodeIgnoreCase(String variantCode);

    boolean existsByProductAndColorAndSize(Product product, ColorOption color, SizeOption size);

    boolean existsByColor_Id(Long colorId);

    long countByMainImageAndIdNot(String mainImage, Long id);

    @EntityGraph(attributePaths = {"color", "size", "images"})
    List<ProductVariant> findByProductIdOrderByVariantCodeAsc(Long productId);

    @EntityGraph(attributePaths = {"color", "size", "images"})
    List<ProductVariant> findByProductIdAndColorIdOrderByVariantCodeAsc(Long productId, Long colorId);

    @EntityGraph(attributePaths = {"product", "color", "size", "images"})
    Optional<ProductVariant> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"product", "color", "size"})
    Optional<ProductVariant> findForCartById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v where v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") Long id);
}