package com.delta.store.order.service;

 
import com.delta.store.order.model.Order;

public interface OrderPdfService {
    byte[] generateOrderPdf(Order order);
}

