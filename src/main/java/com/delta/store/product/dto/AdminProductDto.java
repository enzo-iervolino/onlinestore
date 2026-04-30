package com.delta.store.product.dto;

 

import java.math.BigDecimal;

public class AdminProductDto {

    private Long id;
    private String name;
    private BigDecimal price;

    private Long categoryId;
    private String categoryName;

    private String imageUrl;

    public AdminProductDto() {}

    public AdminProductDto(Long id, String name, BigDecimal price, Long categoryId, String categoryName, String imageUrl) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.imageUrl = imageUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
