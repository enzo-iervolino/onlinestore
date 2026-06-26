package com.delta.store.product.model;

import java.time.LocalDateTime;

public interface ImageLookupEntity {

    Long getId();

    String getCode();

    void setCode(String code);

    String getDescri();

    void setDescri(String descri);

    String getImageUrl();

    void setImageUrl(String imageUrl);

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
