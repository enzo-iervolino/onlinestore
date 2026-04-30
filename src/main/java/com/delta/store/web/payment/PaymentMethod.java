package com.delta.store.web.payment;
 

import java.util.Locale;

public enum PaymentMethod {
    GIFTCARD,
    STRIPE,
    PAYPAL,
    CASH;

    public static PaymentMethod fromSessionValue(String raw) {
        if (raw == null || raw.isBlank()) return STRIPE; // default
        String v = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PaymentMethod.valueOf(v);
        } catch (Exception ex) {
            return STRIPE;
        }
    }
}
