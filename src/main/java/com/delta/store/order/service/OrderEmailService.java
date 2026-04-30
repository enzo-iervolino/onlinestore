package com.delta.store.order.service;

import com.delta.store.order.model.Order;
import jakarta.mail.MessagingException;

public interface OrderEmailService {
    void sendOrderConfirmation(String to, Order order, byte[] receiptPdf) throws MessagingException;
}
