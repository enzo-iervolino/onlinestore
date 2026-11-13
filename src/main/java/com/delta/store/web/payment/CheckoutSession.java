package com.delta.store.web.payment;



import jakarta.servlet.http.HttpSession;

public final class CheckoutSession {

    private CheckoutSession() {}

    // ====== session keys (uguali a quelli che usi già nel template) ======
    public static final String COUPON_CODE = "CHECKOUT_COUPON_CODE";
    public static final String OWNER_TOKEN = "CHECKOUT_OWNER_TOKEN";
    public static final String ATTEMPT = "CHECKOUT_ATTEMPT";
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

    public static void clearIfUnchanged(HttpSession session, com.delta.store.order.model.OrderTempData data) {
        synchronized(session) {
            Object activeId=session.getAttribute(ATTEMPT);
            if (activeId!=null && !activeId.equals(data.getCheckoutAttemptId())) return;
            var cart=(com.delta.store.cart.model.Cart)session.getAttribute(CART);
            if (data.getCartFingerprint()!=null && data.getCartFingerprint().equals(com.delta.store.web.payment.service.CheckoutQuoteService.fingerprint(cart))) clearAll(session);
        }
    }
    /** Clear checkout selections when no cart remains. Keep payment ownership and
     * pending attempts: provider callbacks still need their original snapshot. */
    public static void clearIfCartEmpty(HttpSession session) {
        synchronized (session) {
            var cart = (com.delta.store.cart.model.Cart) session.getAttribute(CART);
            if (cart == null || cart.isEmpty()) clearAll(session);
        }
    }

    public static void clearAll(HttpSession session) {
        session.removeAttribute(CART);
        session.removeAttribute(SHIPPING);

        session.removeAttribute(COUPON_CODE);
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
