package com.delta.store.product.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_variants_code", columnNames = "variant_code"),
                @UniqueConstraint(name = "uk_product_variants_prod_color_size", columnNames = {"product_id", "color_id", "size_id"})
        },
        indexes = {
                @Index(name = "idx_prodvar_product", columnList = "product_id"),
                @Index(name = "idx_prodvar_code", columnList = "variant_code"),
                @Index(name = "idx_prodvar_color", columnList = "color_id"),
                @Index(name = "idx_prodvar_size", columnList = "size_id")
        }
)
public class ProductVariant {

    public static final String MAIN_MEDIA_TYPE_IMAGE = "IMAGE";
    public static final String MAIN_MEDIA_TYPE_VIDEO = "VIDEO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** CODICE UNIVOCO: CODPROD-CODCOLORE-CODTAGLIA */
    @Column(name = "variant_code", nullable = false, unique = true, length = 80)
    private String variantCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id", nullable = false)
    private ColorOption color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "size_id", nullable = false)
    private SizeOption size;

    /** Prezzo variante (se null usa Product.price) */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** Stock (opzionale) */
    @Column(name = "stock_qty")
    private Integer stockQty;

    /** Media principale variante (path o URL): può essere immagine oppure video */
    @Column(name = "main_image", length = 500)
    private String mainImage;

    /** Tipo media principale variante: IMAGE / VIDEO */
    @Column(name = "main_media_type", length = 20)
    private String mainMediaType = MAIN_MEDIA_TYPE_IMAGE;

    /** Poster opzionale quando il media principale variante è un video */
    @Column(name = "main_video_poster", length = 500)
    private String mainVideoPoster;

    @Column(nullable = false)
    private boolean enabled = true;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private Set<ProductVariantImage> images = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        normalizeMainMediaType();

        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        normalizeMainMediaType();
        updatedAt = LocalDateTime.now();
    }

    private void normalizeMainMediaType() {
        if (this.mainMediaType == null || this.mainMediaType.isBlank()) {
            this.mainMediaType = MAIN_MEDIA_TYPE_IMAGE;
            this.mainVideoPoster = null;
            return;
        }

        this.mainMediaType = this.mainMediaType.trim().toUpperCase();
        if (!MAIN_MEDIA_TYPE_VIDEO.equals(this.mainMediaType)) {
            this.mainMediaType = MAIN_MEDIA_TYPE_IMAGE;
        }

        if (MAIN_MEDIA_TYPE_IMAGE.equals(this.mainMediaType)) {
            this.mainVideoPoster = null;
        }
    }

    public void addImage(ProductVariantImage image) {
        images.add(image);
        image.setVariant(this);
    }

    public void removeImage(ProductVariantImage image) {
        images.remove(image);
        image.setVariant(null);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getVariantCode() { return variantCode; }
    public void setVariantCode(String variantCode) { this.variantCode = variantCode; }

    public ColorOption getColor() { return color; }
    public void setColor(ColorOption color) { this.color = color; }

    public SizeOption getSize() { return size; }
    public void setSize(SizeOption size) { this.size = size; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStockQty() { return stockQty; }
    public void setStockQty(Integer stockQty) { this.stockQty = stockQty; }

    public String getMainImage() { return mainImage; }
    public void setMainImage(String mainImage) { this.mainImage = mainImage; }

    public String getMainMediaType() { return mainMediaType; }
    public void setMainMediaType(String mainMediaType) { this.mainMediaType = mainMediaType; }

    public String getMainVideoPoster() { return mainVideoPoster; }
    public void setMainVideoPoster(String mainVideoPoster) { this.mainVideoPoster = mainVideoPoster; }

    public boolean isMainVideo() { return MAIN_MEDIA_TYPE_VIDEO.equalsIgnoreCase(mainMediaType); }
    public boolean isMainImage() { return !isMainVideo(); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Set<ProductVariantImage> getImages() { return images; }
    public void setImages(Set<ProductVariantImage> images) { this.images = images; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
