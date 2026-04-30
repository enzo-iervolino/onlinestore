package com.delta.store.product.dto;

import com.delta.store.product.model.ProductImage;

public class ProductImageDTO {

    private Long id;
    private String imageUrl;
    private String altText;
    private Integer sortOrder;
    private String mediaType = ProductImage.MEDIA_TYPE_IMAGE;
    private String posterUrl;

    public ProductImageDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public boolean isVideo() {
        return ProductImage.MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType);
    }

    public boolean isImage() {
        return !isVideo();
    }
}
