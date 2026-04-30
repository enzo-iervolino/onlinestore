package com.delta.store.product.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "product_image",
        indexes = {
                @Index(name = "idx_prodimg_product", columnList = "product_id")
        }
)
public class ProductImage {

    public static final String MEDIA_TYPE_IMAGE = "IMAGE";
    public static final String MEDIA_TYPE_VIDEO = "VIDEO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Prodotto a cui è collegata l'immagine/media */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** URL / path immagine o video */
    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** Testo alternativo (alt) per SEO/accessibilità */
    @Column(name = "alt_text", length = 255)
    private String altText;

    /** Ordinamento tra i media del prodotto */
    @Column(name = "sort_order")
    private Integer sortOrder;

    /** Tipo media: IMAGE / VIDEO */
    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType = MEDIA_TYPE_IMAGE;

    /** Poster opzionale per il video */
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

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    @Override
    public String toString() {
        return "ProductImage{" +
                "id=" + id +
                ", imageUrl='" + imageUrl + '\'' +
                ", mediaType='" + mediaType + '\'' +
                ", sortOrder=" + sortOrder +
                '}';
    }
}
