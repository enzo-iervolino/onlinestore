package com.delta.store.order.model;

import java.math.BigDecimal;

public class CartItemDTO {

    private Long productId;
    private Long variantId;
    private String productName;

    /**
     * Campo legacy/backward compatibility.
     * Per gli snapshot vecchi può contenere il solo codice disponibile.
     */
    private String sku;

    private String productCode;
    private String variantCode;

    private String color;
    private String size;

    private int quantity;
    private BigDecimal unitPrice;

    public CartItemDTO() {
    }

    public CartItemDTO(Long productId,
                       String productName,
                       String sku,
                       int quantity,
                       BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public CartItemDTO(Long productId,
                       Long variantId,
                       String productName,
                       String sku,
                       String color,
                       String size,
                       int quantity,
                       BigDecimal unitPrice) {
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.sku = sku;
        this.color = color;
        this.size = size;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public CartItemDTO(Long productId,
                       Long variantId,
                       String productName,
                       String sku,
                       String productCode,
                       String variantCode,
                       String color,
                       String size,
                       int quantity,
                       BigDecimal unitPrice) {
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.sku = sku;
        this.productCode = productCode;
        this.variantCode = variantCode;
        this.color = color;
        this.size = size;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getVariantCode() {
        return variantCode;
    }

    public void setVariantCode(String variantCode) {
        this.variantCode = variantCode;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}