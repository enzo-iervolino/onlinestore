package com.delta.store.web.payment;
 

import com.delta.store.order.model.OrderTempData;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;

public record CheckoutPaymentContext(
        OrderTempData data,
        HttpSession session,
        Authentication authentication
) {}
