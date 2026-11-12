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
    public synchronized void add(Product product, int qty) {

        if (product == null || product.getId() == null || qty <= 0) {
            return;
        }

        requireQuantity(qty);
        Long key = product.getId();
        CartItem item = items.get(key);

        if (item == null) {
            if (items.size() >= 100) throw new IllegalArgumentException("Troppi articoli nel carrello");
            items.put(key, new CartItem(product, qty));
        } else {
            int next = Math.addExact(item.getQuantity(), qty);
            requireQuantity(next);
            item.setQuantity(next);
        }
    }

    // ===== ADD VARIANTE =====
    public synchronized void add(ProductVariant variant, int qty) {

        if (variant == null || variant.getId() == null || qty <= 0) {
            return;
        }

        requireQuantity(qty);
        Long key = buildVariantKey(variant.getId());
        CartItem item = items.get(key);

        if (item == null) {
            if (items.size() >= 100) throw new IllegalArgumentException("Troppi articoli nel carrello");
            items.put(key, new CartItem(variant, qty));
        } else {
            int next = Math.addExact(item.getQuantity(), qty);
            requireQuantity(next);
            item.setQuantity(next);
        }
    }

    // ===== BACKWARD COMPATIBILITY =====
    public synchronized void add(Product product) {
        add(product, 1);
    }

    // ===== INCREASE =====
    public synchronized void increase(Long itemId) {

        CartItem item = items.get(itemId);

        if (item != null) {
            requireQuantity(Math.addExact(item.getQuantity(), 1));
            item.increase();
        }
    }

    // ===== DECREASE =====
    public synchronized void decrease(Long itemId) {

        CartItem item = items.get(itemId);

        if (item != null) {

            item.decrease();

            if (item.getQuantity() <= 0) {
                items.remove(itemId);
            }
        }
    }

    // ===== REMOVE =====
    public synchronized void remove(Long itemId) {
        items.remove(itemId);
    }

    // ===== CLEAR CART =====
    public synchronized void clear() {
        items.clear();
    }

    // ===== GET ITEMS =====
    public synchronized Collection<CartItem> getItems() {
        return items.values().stream().map(CartItem::new).toList();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    // ===== COUNT =====
    public synchronized int getCount() {

        return items.values()
                .stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    // ===== TOTAL =====
    public synchronized BigDecimal getTotal() {

        return items.values()
                .stream()
                .map(CartItem::getRowTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireQuantity(int qty) {
        if (qty < 1 || qty > 999) throw new IllegalArgumentException("Quantità o numero articoli non consentito");
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