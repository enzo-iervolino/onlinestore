package com.delta.store.shipping.service;

import com.delta.store.shipping.model.ShippingCost;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ShippingService {

    /**
     * Calcola il costo di spedizione in base al subtotale del carrello.
     */
    BigDecimal calculateShipping(BigDecimal subtotal);

    List<ShippingCost> findAll();

    Optional<ShippingCost> findById(Long id);

    ShippingCost save(ShippingCost shippingCost);

    void deleteById(Long id);

    boolean existsById(Long id);

    boolean hasActiveOverlap(Long excludedId, BigDecimal minTotal, BigDecimal maxTotal);
}
