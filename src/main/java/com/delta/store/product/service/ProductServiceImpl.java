package com.delta.store.product.service;

import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.mapper.ProductMapper;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.repository.ProductCategoryRepository;
import com.delta.store.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public ProductServiceImpl(ProductRepository productRepository,
                              ProductCategoryRepository productCategoryRepository) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAllByOrderByIdDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Override
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> search(ProductCategory category, String text) {
        String normalizedText = (text == null || text.isBlank()) ? null : text.trim();

        if (normalizedText == null) {
            if (category != null) {
                return productRepository.findByCategoryOrderByIdDesc(category);
            }
            return productRepository.findAllByOrderByIdDesc();
        }

        return productRepository.search(category, normalizedText);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findWithImagesById(Long id) {
        return productRepository.findWithImagesById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategory> findEnabledCategories() {
        return productCategoryRepository.findByEnabledTrueOrderBySortOrderAscNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductCategory> findCategoryById(Long id) {
        return productCategoryRepository.findById(id);
    }

    // =========================
    // DTO
    // =========================

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> findAllDto() {
        return ProductMapper.toDtoList(productRepository.findAllByOrderByIdDesc());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDTO> findByIdDto(Long id) {
        return productRepository.findById(id)
                .map(ProductMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDTO> findWithImagesByIdDto(Long id) {
        return productRepository.findWithImagesById(id)
                .map(ProductMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDTO> searchDto(ProductCategory category, String text) {
        return ProductMapper.toDtoList(search(category, text));
    }
}