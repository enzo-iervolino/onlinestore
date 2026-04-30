package com.delta.store.web.payment;



import jakarta.servlet.http.HttpSession;

public final class CheckoutSession {

    private CheckoutSession() {}

    // ====== session keys (uguali a quelli che usi già nel template) ======
    public static final String CART = "CART";
    public static final String SHIPPING = "CHECKOUT_SHIPPING";
    public static final String PAYMENT_METHOD = "CHECKOUT_PAYMENT_METHOD";

    public static final String DISCOUNT_AMOUNT = "DISCOUNT_AMOUNT";
    public static final String DISCOUNT_PERCENT = "DISCOUNT_PERCENT";

    public static final String GIFT_CODE = "GIFT_CODE";
    public static final String GIFT_REMAINING = "GIFT_REMAINING";
    public static final String GIFT_AMOUNT = "GIFT_AMOUNT";
    public static final String GIFT_PROPOSAL = "GIFT_PROPOSAL";

    // utile per Stripe/PayPal (salviamo snapshot checkout)
    public static final String TEMP_DATA = "CHECKOUT_TEMP_DATA";

    public static void clearAll(HttpSession session) {
        session.removeAttribute(CART);
        session.removeAttribute(SHIPPING);

        session.removeAttribute(DISCOUNT_AMOUNT);
        session.removeAttribute(DISCOUNT_PERCENT);

        session.removeAttribute(GIFT_CODE);
        session.removeAttribute(GIFT_REMAINING);
        session.removeAttribute(GIFT_AMOUNT);
        session.removeAttribute(GIFT_PROPOSAL);

        session.removeAttribute(PAYMENT_METHOD);
        session.removeAttribute(TEMP_DATA);
    }
}
