package com.delta.store.shipping.repository;

import com.delta.store.shipping.model.ShippingCost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ShippingCostRepository extends JpaRepository<ShippingCost, Long> {

    List<ShippingCost> findAllByOrderByMinTotalAscMaxTotalAscIdAsc();

    @Query("""
        SELECT s
        FROM ShippingCost s
        WHERE s.active = true
          AND :subtotal >= s.minTotal
          AND (s.maxTotal IS NULL OR :subtotal <= s.maxTotal)
        ORDER BY s.minTotal DESC, s.id DESC
    """)
    List<ShippingCost> findMatchingCosts(@Param("subtotal") BigDecimal subtotal, Pageable pageable);

    default Optional<ShippingCost> findBestMatch(BigDecimal subtotal) {
        return findMatchingCosts(subtotal, Pageable.ofSize(1))
                .stream()
                .findFirst();
    }
}
