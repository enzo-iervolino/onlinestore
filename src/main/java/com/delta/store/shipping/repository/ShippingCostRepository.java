package com.delta.store.shipping.repository;

 

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.delta.store.shipping.model.ShippingCost;

import java.math.BigDecimal;
import java.util.Optional;

public interface ShippingCostRepository extends JpaRepository<ShippingCost, Long> {

    @Query("""
        SELECT s
        FROM ShippingCost s
        WHERE s.active = true
          AND :subtotal >= s.minTotal
          AND (s.maxTotal IS NULL OR :subtotal <= s.maxTotal)
        ORDER BY s.minTotal DESC
    """)
    Optional<ShippingCost> findBestMatch(@Param("subtotal") BigDecimal subtotal);
}
