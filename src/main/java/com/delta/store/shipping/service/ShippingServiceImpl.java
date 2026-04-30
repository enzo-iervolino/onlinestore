package com.delta.store.shipping.service;

 

import org.springframework.stereotype.Service;

import com.delta.store.shipping.model.ShippingCost;
import com.delta.store.shipping.repository.ShippingCostRepository;

import java.math.BigDecimal;

@Service
public class ShippingServiceImpl implements ShippingService {

    private final ShippingCostRepository shippingCostRepository;

    public ShippingServiceImpl(ShippingCostRepository shippingCostRepository) {
        this.shippingCostRepository = shippingCostRepository;
    }

    @Override
    public BigDecimal calculateShipping(BigDecimal subtotal) {

        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return shippingCostRepository
                .findBestMatch(subtotal)
                .map(ShippingCost::getCost)
                .orElse(BigDecimal.ZERO);
    }
}
