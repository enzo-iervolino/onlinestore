package com.delta.store.product.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "product_variant_images",
        indexes = @Index(name = "idx_prodvarimg_variant", columnList = "variant_id")
)
public class ProductVariantImage {

    public static final String MEDIA_TYPE_IMAGE = "IMAGE";
    public static final String MEDIA_TYPE_VIDEO = "VIDEO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType = MEDIA_TYPE_IMAGE;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @PrePersist
    @PreUpdate
    public void normalizeMediaType() {
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = MEDIA_TYPE_IMAGE;
        } else {
            mediaType = mediaType.trim().toUpperCase();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public void setVariant(ProductVariant variant) {
        this.variant = variant;
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
        return MEDIA_TYPE_VIDEO.equalsIgnoreCase(mediaType);
    }

    public boolean isImage() {
        return !isVideo();
    }
}