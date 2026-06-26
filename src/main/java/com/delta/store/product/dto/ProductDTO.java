package com.delta.store.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class ProductDTO {

    private Long id;
    private UUID publicId;
    private String codpro;
    private String name;

    private Long categoryId;
    private String categoryCode;
    private String categoryName;

    private Integer stockQty;
    private Long groupId;
    private Long subgroupId;
    private Long brandId;
    private Long familyId;
    private Long subfamilyId;

    private String description;
    private String materials;
    private String shippingInfo;

    private BigDecimal price;
    private String image;
    private String mainMediaType;
    private String mainVideoPoster;

    private Set<ProductImageDTO> images = new LinkedHashSet<>();
    private Set<ProductVariantDTO> variants = new LinkedHashSet<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProductDTO() {
    }

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getStockQty() {
        return stockQty;
    }

    public void setStockQty(Integer stockQty) {
        this.stockQty = stockQty;
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

    public Set<ProductImageDTO> getImages() {
        return images;
    }

    public void setImages(Set<ProductImageDTO> images) {
        this.images = images;
    }

    public Set<ProductVariantDTO> getVariants() {
        return variants;
    }

    public void setVariants(Set<ProductVariantDTO> variants) {
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
}