package com.delta.store.product.dto;

 

import com.delta.store.product.dto.ProductImageDTO;
import com.delta.store.product.dto.ProductVariantDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ShopProductDetailDTO {

    private Long productId;
    private String productCode;
    private String name;
    private String description;
    private String materials;
    private String shippingInfo;
    private BigDecimal basePrice;
    private String mainImage;
    private String mainMediaType;
    private String mainVideoPoster;

    private List<ProductImageDTO> images = new ArrayList<>();
    private List<ProductVariantDTO> variants = new ArrayList<>();

    private boolean hasVariants;
    private boolean showSizeSelector;
    private boolean showColorSelector;

    private String singleSizeLabel;
    private String singleColorLabel;

    private Long defaultVariantId;
    private String defaultSizeCode;
    private String defaultColorCode;

    public ShopProductDetailDTO() {
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getMaterials() {
        return materials;
    }

    public String getShippingInfo() {
        return shippingInfo;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public String getMainImage() {
        return mainImage;
    }

    public String getMainMediaType() {
        return mainMediaType;
    }

    public String getMainVideoPoster() {
        return mainVideoPoster;
    }

    public List<ProductImageDTO> getImages() {
        return images;
    }

    public List<ProductVariantDTO> getVariants() {
        return variants;
    }

    public boolean isHasVariants() {
        return hasVariants;
    }

    public boolean isShowSizeSelector() {
        return showSizeSelector;
    }

    public boolean isShowColorSelector() {
        return showColorSelector;
    }

    public String getSingleSizeLabel() {
        return singleSizeLabel;
    }

    public String getSingleColorLabel() {
        return singleColorLabel;
    }

    public Long getDefaultVariantId() {
        return defaultVariantId;
    }

    public String getDefaultSizeCode() {
        return defaultSizeCode;
    }

    public String getDefaultColorCode() {
        return defaultColorCode;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setMaterials(String materials) {
        this.materials = materials;
    }

    public void setShippingInfo(String shippingInfo) {
        this.shippingInfo = shippingInfo;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public void setMainImage(String mainImage) {
        this.mainImage = mainImage;
    }

    public void setMainMediaType(String mainMediaType) {
        this.mainMediaType = mainMediaType;
    }

    public void setMainVideoPoster(String mainVideoPoster) {
        this.mainVideoPoster = mainVideoPoster;
    }

    public void setImages(List<ProductImageDTO> images) {
        this.images = images;
    }

    public void setVariants(List<ProductVariantDTO> variants) {
        this.variants = variants;
    }

    public void setHasVariants(boolean hasVariants) {
        this.hasVariants = hasVariants;
    }

    public void setShowSizeSelector(boolean showSizeSelector) {
        this.showSizeSelector = showSizeSelector;
    }

    public void setShowColorSelector(boolean showColorSelector) {
        this.showColorSelector = showColorSelector;
    }

    public void setSingleSizeLabel(String singleSizeLabel) {
        this.singleSizeLabel = singleSizeLabel;
    }

    public void setSingleColorLabel(String singleColorLabel) {
        this.singleColorLabel = singleColorLabel;
    }

    public void setDefaultVariantId(Long defaultVariantId) {
        this.defaultVariantId = defaultVariantId;
    }

    public void setDefaultSizeCode(String defaultSizeCode) {
        this.defaultSizeCode = defaultSizeCode;
    }

    public void setDefaultColorCode(String defaultColorCode) {
        this.defaultColorCode = defaultColorCode;
    }
}
