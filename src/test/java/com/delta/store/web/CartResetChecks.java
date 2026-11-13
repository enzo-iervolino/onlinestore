package com.delta.store.web;

import com.delta.store.cart.controller.CartController;
import com.delta.store.cart.controller.CartPageController;
import com.delta.store.cart.model.Cart;
import com.delta.store.product.model.Product;
import com.delta.store.web.payment.CheckoutSession;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Regression checks without database or payment provider calls. */
public class CartResetChecks {
    private static final String[] SELECTIONS = {
        CheckoutSession.COUPON_CODE, CheckoutSession.DISCOUNT_AMOUNT,
        CheckoutSession.DISCOUNT_PERCENT, CheckoutSession.GIFT_CODE,
        CheckoutSession.GIFT_REMAINING, CheckoutSession.GIFT_AMOUNT,
        CheckoutSession.GIFT_PROPOSAL, CheckoutSession.SHIPPING,
        CheckoutSession.PAYMENT_METHOD, CheckoutSession.TEMP_DATA
    };

    public static void main(String[] args) {
        CartController rest = new CartController(null, null, null);
        CartPageController page = new CartPageController();
        for (int scenario = 0; scenario < 9; scenario++) {
            Map<String, Object> state = new HashMap<>();
            HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getAttribute" -> state.get(a[0]);
                    case "setAttribute" -> { state.put((String) a[0], a[1]); yield null; }
                    case "removeAttribute" -> state.remove(a[0]);
                    default -> throw new UnsupportedOperationException(m.getName());
                });
            Cart cart = new Cart();
            Product product = new Product();
            product.setId(1L); product.setPrice(BigDecimal.TEN);
            if (scenario < 6 || scenario == 8) cart.add(product, scenario == 8 ? 2 : 1);
            if (scenario != 7) state.put(CheckoutSession.CART, cart);
            for (String key : SELECTIONS) state.put(key, "selection");
            state.put(CheckoutSession.OWNER_TOKEN, "owner");
            state.put(CheckoutSession.ATTEMPT, "pending");
            switch (scenario) {
                case 0 -> rest.clear(session);
                case 1 -> rest.remove(1L, session);
                case 2 -> rest.decrease(1L, session);
                case 3 -> page.clearFromPage(session);
                case 4 -> page.removeFromPage(1L, session);
                case 5 -> page.decreaseFromPage(1L, session);
                case 6, 7 -> rest.view(session);
                case 8 -> rest.decrease(1L, session);
            }
            for (String key : SELECTIONS) {
                if (state.containsKey(key) != (scenario == 8))
                    throw new AssertionError("scenario " + scenario + ": " + key);
            }
            if (!"owner".equals(state.get(CheckoutSession.OWNER_TOKEN))
                    || !"pending".equals(state.get(CheckoutSession.ATTEMPT)))
                throw new AssertionError("Pending payment ownership lost");
            if (scenario != 8) {
                rest.view(session);
                Cart next = (Cart) state.get(CheckoutSession.CART);
                next.add(product, 1);
                for (String key : SELECTIONS)
                    if (state.containsKey(key)) throw new AssertionError("Stale selection in new cart");
            }
        }
        System.out.println("PASS: 9 cart reset scenarios, new cart clean, pending payment ownership preserved");
    }
}
