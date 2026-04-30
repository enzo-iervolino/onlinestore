package com.delta.store.cart.model;

import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;

import java.io.Serializable;
import java.math.BigDecimal;

public class CartItem implements Serializable {

    private final Long id;

    private final Long productId;
    private final Long variantId;

    private final String productCode;
    private final String variantCode;

    private final String name;
    private final String image;
    private final String color;
    private final String size;
    private final BigDecimal price;

    private int quantity;

    public CartItem(Product product, int quantity) {
        this.id = product.getId();
        this.productId = product.getId();
        this.variantId = null;
        this.productCode = product.getCodpro();
        this.variantCode = null;
        this.name = product.getName();
        this.image = product.getImage();
        this.color = null;
        this.size = null;
        this.price = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
        this.quantity = quantity;
    }

    public CartItem(ProductVariant variant, int quantity) {

        this.id = buildVariantKey(variant.getId());
        this.productId = variant.getProduct() != null ? variant.getProduct().getId() : null;
        this.variantId = variant.getId();

        this.productCode = variant.getProduct() != null ? variant.getProduct().getCodpro() : null;
        this.variantCode = variant.getVariantCode();

        this.name = variant.getProduct() != null ? variant.getProduct().getName() : "";

        this.image = resolveImage(variant);

        // ===== QUI MOSTRIAMO IL NOME UTENTE =====
        this.color = variant.getColor() != null ? variant.getColor().getNameIt() : null;
        this.size  = variant.getSize()  != null ? variant.getSize().getNameIt()  : null;

        this.price = resolvePrice(variant);

        this.quantity = quantity;
    }

    public void increase() {
        quantity++;
    }

    public void decrease() {
        quantity--;
    }

    public BigDecimal getRowTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    private BigDecimal resolvePrice(ProductVariant variant) {

        if (variant.getPrice() != null) {
            return variant.getPrice();
        }

        if (variant.getProduct() != null && variant.getProduct().getPrice() != null) {
            return variant.getProduct().getPrice();
        }

        return BigDecimal.ZERO;
    }

    private String resolveImage(ProductVariant variant) {

        if (variant.getMainImage() != null && !variant.getMainImage().isBlank()) {
            return variant.getMainImage();
        }

        if (variant.getProduct() != null) {
            return variant.getProduct().getImage();
        }

        return null;
    }

    private Long buildVariantKey(Long variantId) {
        return -variantId;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getVariantId() {
        return variantId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getVariantCode() {
        return variantCode;
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getColor() {
        return color;
    }

    public String getSize() {
        return size;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}