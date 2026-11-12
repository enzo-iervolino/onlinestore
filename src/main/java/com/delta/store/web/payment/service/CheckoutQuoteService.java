package com.delta.store.web.payment.service;

import com.delta.store.cart.model.*;
import com.delta.store.product.model.*;
import com.delta.store.product.repository.*;
import com.delta.store.order.model.OrderTempData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class CheckoutQuoteService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    public CheckoutQuoteService(ProductRepository products, ProductVariantRepository variants) {
        this.products = products; this.variants = variants;
    }
    @Transactional(readOnly = true)
    public Cart refresh(Cart original) {
        if (original == null) return new Cart();
        Cart fresh = new Cart();
        for (CartItem item : original.getItems()) {
            if (item.getQuantity() < 1 || item.getQuantity() > 999) throw new IllegalArgumentException("Quantità non valida");
            if (item.getVariantId() != null) {
                ProductVariant v = variants.findForCartById(item.getVariantId()).orElseThrow(() -> new IllegalArgumentException("Una variante non è più disponibile: rimuovila dal carrello"));
                if (!v.isEnabled() || v.getProduct() == null || !v.getProduct().getId().equals(item.getProductId())) throw new IllegalArgumentException("Variante non disponibile: rimuovila dal carrello");
                checkPrice(v.getPrice() != null ? v.getPrice() : v.getProduct().getPrice());
                fresh.add(v, item.getQuantity());
            } else {
                Product p = products.findById(item.getProductId()).orElseThrow(() -> new IllegalArgumentException("Un prodotto non è più disponibile: rimuovilo dal carrello"));
                if (!p.getVariants().isEmpty()) throw new IllegalArgumentException("Scegli la variante del prodotto " + p.getName());
                checkPrice(p.getPrice());
                fresh.add(p, item.getQuantity());
            }
        }
        return fresh;
    }
    private void checkPrice(BigDecimal price) {
        if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("9999999.99")) > 0) throw new IllegalArgumentException("Prezzo non disponibile");
    }
    public static String fingerprint(Cart cart) {
        StringBuilder s = new StringBuilder();
        if (cart != null) cart.getItems().stream().sorted(java.util.Comparator.comparing(CartItem::getId)).forEach(i -> s.append(i.getId()).append(':').append(i.getQuantity()).append(':').append(i.getPrice().stripTrailingZeros()).append(';'));
        return hash(s.toString());
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static BigDecimal money(BigDecimal value) {
        if (value == null) return new BigDecimal("0.00");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
    public static void validate(OrderTempData d) {
        require(d.getCustomerName(), 2, 160, "Nome e cognome");
        require(d.getCustomerEmail(), 3, 160, "Email");
        if (!d.getCustomerEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalArgumentException("Email non valida");
        require(d.getCustomerPhone(), 5, 30, "Telefono");
        require(d.getShippingAddress1(), 3, 200, "Indirizzo");
        require(d.getShippingCity(), 2, 100, "Città");
        if (d.getShippingZip() == null || !d.getShippingZip().matches("[0-9]{5}")) throw new IllegalArgumentException("CAP non valido");
        if (d.getShippingProvince() == null || !d.getShippingProvince().matches("[A-Za-z]{2}")) throw new IllegalArgumentException("Provincia: inserisci la sigla di due lettere");
        if (d.getCartItems() == null || d.getCartItems().isEmpty() || d.getCartItems().size() > 100) throw new IllegalArgumentException("Carrello non valido");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var item : d.getCartItems()) {
            if (item.getQuantity() < 1 || item.getQuantity() > 999 || item.getProductId() == null || item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) throw new IllegalArgumentException("Riga carrello non valida");
            subtotal = subtotal.add(money(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        BigDecimal discount = money(d.getDiscountValue()), shipping = money(d.getShippingCost()), gift = money(d.getGiftCardAmount()), external = money(d.getTotalAmount());
        if (discount.signum() < 0 || discount.compareTo(subtotal) > 0 || shipping.signum() < 0 || gift.signum() < 0 || external.signum() < 0 || external.compareTo(new BigDecimal("9999999.99")) > 0 || subtotal.compareTo(money(d.getSubtotal())) != 0 || subtotal.subtract(discount).add(shipping).compareTo(gift.add(external)) != 0) throw new IllegalArgumentException("Totali checkout non coerenti");
        if ("GIFTCARD".equals(d.getPaymentMethod()) && external.signum() != 0) throw new IllegalArgumentException("La gift card non copre tutto l'ordine: scegli Stripe o PayPal");
    }
    private static void require(String value, int min, int max, String field) {
        if (value == null || value.trim().length() < min || value.length() > max || value.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException(field + " mancante o non valido");
    }
}
