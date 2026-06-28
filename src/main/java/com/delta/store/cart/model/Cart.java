package com.delta.store.cart.model;

import com.delta.store.product.model.Product;
import com.delta.store.product.model.ProductVariant;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Cart implements Serializable {

    private final Map<Long, CartItem> items = new LinkedHashMap<>();

    // ===== ADD PRODOTTO BASE =====
    public void add(Product product, int qty) {

        if (product == null || product.getId() == null || qty <= 0) {
            return;
        }

        Long key = product.getId();
        CartItem item = items.get(key);

        if (item == null) {
            items.put(key, new CartItem(product, qty));
        } else {
            item.setQuantity(item.getQuantity() + qty);
        }
    }

    // ===== ADD VARIANTE =====
    public void add(ProductVariant variant, int qty) {

        if (variant == null || variant.getId() == null || qty <= 0) {
            return;
        }

        Long key = buildVariantKey(variant.getId());
        CartItem item = items.get(key);

        if (item == null) {
            items.put(key, new CartItem(variant, qty));
        } else {
            item.setQuantity(item.getQuantity() + qty);
        }
    }

    // ===== BACKWARD COMPATIBILITY =====
    public void add(Product product) {
        add(product, 1);
    }

    // ===== INCREASE =====
    public void increase(Long itemId) {

        CartItem item = items.get(itemId);

        if (item != null) {
            item.increase();
        }
    }

    // ===== DECREASE =====
    public void decrease(Long itemId) {

        CartItem item = items.get(itemId);

        if (item != null) {

            item.decrease();

            if (item.getQuantity() <= 0) {
                items.remove(itemId);
            }
        }
    }

    // ===== REMOVE =====
    public void remove(Long itemId) {
        items.remove(itemId);
    }

    // ===== CLEAR CART =====
    public void clear() {
        items.clear();
    }

    // ===== GET ITEMS =====
    public Collection<CartItem> getItems() {
        return items.values();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    // ===== COUNT =====
    public int getCount() {

        return items.values()
                .stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    // ===== TOTAL =====
    public BigDecimal getTotal() {

        return items.values()
                .stream()
                .map(CartItem::getRowTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ===== VARIANT KEY =====
    private Long buildVariantKey(Long variantId) {

        /*
         * Usiamo una chiave negativa per le varianti
         * così non entriamo in conflitto con productId
         */
        return -variantId;
    }
}