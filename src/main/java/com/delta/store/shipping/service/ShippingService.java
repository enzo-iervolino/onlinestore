package com.delta.store.shipping.service;

 

import java.math.BigDecimal;

public interface ShippingService {

    /**
     * Calcola il costo di spedizione in base al subtotale del carrello.
     */
    BigDecimal calculateShipping(BigDecimal subtotal);
}
