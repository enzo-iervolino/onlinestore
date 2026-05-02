package com.delta.store.product.service;

import com.delta.store.product.dto.ProductDTO;
import com.delta.store.product.mapper.ProductMapper;
import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductCategory;
import com.delta.store.product.repository.ProductCategoryRepository;
import com.delta.store.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    // =========================
    // IMPORT
    // =========================

    @Override
    public Product importProduct(ProductDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("DTO prodotto non valido");
        }

        String codpro = dto.getCodpro() != null ? dto.getCodpro().trim() : null;
        String name = dto.getName() != null ? dto.getName().trim() : null;

        if (codpro == null || codpro.isBlank()) {
            throw new IllegalArgumentException("Codice prodotto obbligatorio");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome prodotto obbligatorio");
        }

        if (dto.getCategoryId() == null) {
            throw new IllegalArgumentException("Categoria prodotto obbligatoria");
        }

        if (dto.getPrice() == null) {
            throw new IllegalArgumentException("Prezzo prodotto obbligatorio");
        }

        if (dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Prezzo prodotto non può essere negativo");
        }

        if (dto.getStockQty() != null && dto.getStockQty() < 0) {
            throw new IllegalArgumentException("Stock prodotto non può essere negativo");
        }

        ProductCategory category = productCategoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Categoria non trovata con ID: " + dto.getCategoryId()
                ));

        Product product = productRepository.findByCodpro(codpro)
                .orElseGet(Product::new);

        product.setCodpro(codpro);
        product.setName(name);
        product.setCategory(category);
        product.setStockQty(dto.getStockQty());
        product.setDescription(dto.getDescription());
        product.setMaterials(dto.getMaterials());
        product.setShippingInfo(dto.getShippingInfo());
        product.setPrice(dto.getPrice());

        return productRepository.save(product);
    }
}