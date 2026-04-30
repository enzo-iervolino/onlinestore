package com.delta.store.order.service;

import com.delta.store.order.model.OrderType;

import java.util.List;
import java.util.Optional;

public interface OrderTypeService {

    List<OrderType> findAll();

    List<OrderType> search(String q);

    Optional<OrderType> findById(Long id);

    Optional<OrderType> findByCodeIgnoreCase(String code);

    OrderType save(OrderType orderType);

    void deleteById(Long id);
}