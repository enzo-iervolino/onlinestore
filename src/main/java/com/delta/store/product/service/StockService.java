package com.delta.store.product.service;

 

import com.delta.store.order.model.Order;

public interface StockService {
    void decrementStockForPaidOrder(Order order);
}