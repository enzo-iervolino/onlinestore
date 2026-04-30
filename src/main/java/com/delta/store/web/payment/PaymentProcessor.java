package com.delta.store.web.payment;

 

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public interface PaymentProcessor {

    PaymentMethod supports();

    /**
     * Ritorna una view Spring MVC (es: "redirect:/checkout/success?orderId=1"
     * oppure "redirect:https://checkout.stripe.com/..." ecc).
     */
    String start(CheckoutPaymentContext ctx, RedirectAttributes ra);
}
