package com.delta.store.product.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_codpro", columnList = "codpro"),
                @Index(name = "idx_products_group", columnList = "group_id"),
                @Index(name = "idx_products_subgroup", columnList = "subgroup_id"),
                @Index(name = "idx_products_brand", columnList = "brand_id"),
                @Index(name = "idx_products_family", columnList = "family_id"),
                @Index(name = "idx_products_subfamily", columnList = "subfamily_id")
        }
)
public class Product {

    public static final String MAIN_MEDIA_TYPE_IMAGE = "IMAGE";
    public static final String MAIN_MEDIA_TYPE_VIDEO = "VIDEO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * UUID pubblico da usare nelle API / URL
     */
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false, columnDefinition = "uuid")
    private UUID publicId;

    /** Codice prodotto */
    @Column(nullable = false, unique = true, length = 50)
    private String codpro;

    /** Nome prodotto */
    @Column(nullable = false, length = 255)
    private String name;

    /** Categoria principale */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    /** Stock (opzionale) */
    @Column(name = "stock_qty")
    private Integer stockQty;
    
    
    /** Gruppo logico */
    @Column(name = "group_id")
    private Long groupId;

    /** Sottogruppo (se usato) */
    @Column(name = "subgroup_id")
    private Long subgroupId;

    /** Brand */
    @Column(name = "brand_id")
    private Long brandId;

    /** Famiglia */
    @Column(name = "family_id")
    private Long familyId;

    /** Sottofamiglia */
    @Column(name = "subfamily_id")
    private Long subfamilyId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "text")
    private String materials;

    @Column(columnDefinition = "text")
    private String shippingInfo;

    /** Prezzo base (IVA inclusa) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Media principale (path o URL): può essere immagine oppure video */
    @Column(length = 500)
    private String image;

    /** Tipo media principale: IMAGE / VIDEO */
    @Column(name = "main_media_type", nullable = false, length = 20)
    private String mainMediaType = MAIN_MEDIA_TYPE_IMAGE;

    /** Poster opzionale da usare quando il media principale è un video */
    @Column(name = "main_video_poster", length = 500)
    private String mainVideoPoster;

    /** Immagini aggiuntive (tabella product_image) */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private Set<ProductImage> images = new LinkedHashSet<>();

    /** Varianti (colori/taglie) */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<ProductVariant> variants = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ----------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------

    @PrePersist
    public void prePersist() {
        normalizeMainMediaType();

        if (this.publicId == null) {
            this.publicId = UUID.randomUUID();
        }

        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
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

    // ----------------------------------------------------
    // Helper relazioni
    // ----------------------------------------------------

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    public void removeImage(ProductImage image) {
        images.remove(image);
        image.setProduct(null);
    }

    public void addVariant(ProductVariant variant) {
        variants.add(variant);
        variant.setProduct(this);
    }

    public void removeVariant(ProductVariant variant) {
        variants.remove(variant);
        variant.setProduct(null);
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

    public UUID getPublicId() {
        return publicId;
    }

    public void setPublicId(UUID publicId) {
        this.publicId = publicId;
    }

    public String getCodpro() {
        return codpro;
    }

    public void setCodpro(String codpro) {
        this.codpro = codpro;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getSubgroupId() {
        return subgroupId;
    }

    public void setSubgroupId(Long subgroupId) {
        this.subgroupId = subgroupId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public Long getSubfamilyId() {
        return subfamilyId;
    }

    public void setSubfamilyId(Long subfamilyId) {
        this.subfamilyId = subfamilyId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getMainMediaType() {
        return mainMediaType;
    }

    public void setMainMediaType(String mainMediaType) {
        this.mainMediaType = mainMediaType;
    }

    public String getMainVideoPoster() {
        return mainVideoPoster;
    }

    public void setMainVideoPoster(String mainVideoPoster) {
        this.mainVideoPoster = mainVideoPoster;
    }

    public boolean isMainVideo() {
        return MAIN_MEDIA_TYPE_VIDEO.equalsIgnoreCase(mainMediaType);
    }

    public boolean isMainImage() {
        return !isMainVideo();
    }

    public Set<ProductImage> getImages() {
        return images;
    }

    public void setImages(Set<ProductImage> images) {
        this.images = images;
    }

    public Set<ProductVariant> getVariants() {
        return variants;
    }

    public void setVariants(Set<ProductVariant> variants) {
        this.variants = variants;
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
        return "Product{" +
                "id=" + id +
                ", category=" + (category != null ? category.getCode() : null) +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}';
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMaterials() {
        return materials;
    }

    public void setMaterials(String materials) {
        this.materials = materials;
    }

    public String getShippingInfo() {
        return shippingInfo;
    }

    public void setShippingInfo(String shippingInfo) {
        this.shippingInfo = shippingInfo;
    }

	public Integer getStockQty() {
		return stockQty;
	}

	public void setStockQty(Integer stockQty) {
		this.stockQty = stockQty;
	}
}