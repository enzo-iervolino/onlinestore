package com.delta.store.product.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_categories",
        indexes = {
                @Index(name = "idx_prodcat_code", columnList = "code", unique = true),
                @Index(name = "idx_prodcat_enabled", columnList = "enabled")
        }
)
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Codice univoco categoria (es. PIZZE, BEVANDE, ECC) */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Nome visualizzato al cliente/admin */
    @Column(nullable = false, length = 255)
    private String name;

    /** Descrizione estesa (opzionale) */
    @Column(columnDefinition = "text")
    private String description;

    /** URL immagine categoria (banner/cover) */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Categoria abilitata per la vendita */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Ordinamento per menù/admin */
    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ----------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ----------------------------------------------------
    // Getter / Setter
    // ----------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }


    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }


    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }


    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }


    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }


    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }


    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ProductCategory{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", enabled=" + enabled +
                ", sortOrder=" + sortOrder +
                '}';
    }
}