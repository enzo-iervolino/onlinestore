package com.delta.store.web.payment;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

public interface PaymentProcessor {

    PaymentMethod supports();

    String start(CheckoutPaymentContext ctx, RedirectAttributes ra);
}