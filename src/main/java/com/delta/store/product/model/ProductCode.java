package com.delta.store.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "product_codes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_codes_normalized", columnNames = "normalized_code")
        },
        indexes = {
                @Index(name = "idx_product_codes_product", columnList = "product_id"),
                @Index(name = "idx_product_codes_variant", columnList = "variant_id"),
                @Index(name = "idx_product_codes_type", columnList = "code_type")
        }
)
public class ProductCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(name = "code_type", nullable = false, length = 20)
    private ArticleCodeType codeType = ArticleCodeType.OTHER;

    @Column(name = "code_value", nullable = false, length = 120)
    private String codeValue;

    @Column(name = "normalized_code", nullable = false, unique = true, length = 120)
    private String normalizedCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        normalizeAndValidate();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        normalizeAndValidate();
    }

    private void normalizeAndValidate() {
        boolean productOwner = product != null;
        boolean variantOwner = variant != null;

        if (productOwner == variantOwner) {
            throw new IllegalStateException("Un codice articolo deve appartenere a un prodotto oppure a una variante.");
        }

        if (codeValue == null || codeValue.isBlank()) {
            throw new IllegalStateException("Il valore del codice articolo è obbligatorio.");
        }

        codeValue = codeValue.trim();
        normalizedCode = normalizeCode(codeValue);

        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new IllegalStateException("Il codice articolo non è valido.");
        }

        if (codeType == null) {
            codeType = ArticleCodeType.OTHER;
        }
    }

    public static String normalizeCode(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value
                .replaceAll("[\\p{Cntrl}]", "")
                .trim()
                .replaceAll("\\s+", " ");

        if (cleaned.isBlank()) {
            return null;
        }

        if (cleaned.matches("[0-9\\s-]+")) {
            cleaned = cleaned.replaceAll("[\\s-]", "");
        }

        return cleaned.toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
    }

    public ArticleCodeType getCodeType() {
        return codeType;
    }

    public void setCodeType(ArticleCodeType codeType) {
        this.codeType = codeType;
    }

    public String getCodeValue() {
        return codeValue;
    }

    public void setCodeValue(String codeValue) {
        this.codeValue = codeValue;
        this.normalizedCode = normalizeCode(codeValue);
    }

    public String getNormalizedCode() {
        return normalizedCode;
    }

    public void setNormalizedCode(String normalizedCode) {
        this.normalizedCode = normalizedCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
