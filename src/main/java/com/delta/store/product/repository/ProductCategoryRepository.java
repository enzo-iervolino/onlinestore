package com.delta.store.product.repository;

import com.delta.store.product.model.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByCode(String code);

    List<ProductCategory> findByEnabledTrueOrderBySortOrderAscNameAsc();
}




