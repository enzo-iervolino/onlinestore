package com.delta.store.product.service;

import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;

import java.util.List;
import java.util.Optional;

public interface ProductService {

    List<Product> findAll();

    Optional<Product> findById(Long id);

    Product save(Product product);

    void deleteById(Long id);

    List<Product> search(ProductCategory category, String text);

    Optional<Product> findWithImagesById(Long id);

    List<ProductCategory> findEnabledCategories();

    Optional<ProductCategory> findCategoryById(Long id);

    // =========================
    // DTO
    // =========================

    List<ProductDTO> findAllDto();

    Optional<ProductDTO> findByIdDto(Long id);

    Optional<ProductDTO> findWithImagesByIdDto(Long id);

    List<ProductDTO> searchDto(ProductCategory category, String text);

    // =========================
    // IMPORT
    // =========================

    Product importProduct(ProductDTO dto);
}