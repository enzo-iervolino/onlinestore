package com.delta.store.order.repository;

import com.delta.store.order.model.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderTypeRepository extends JpaRepository<OrderType, Long> {

    Optional<OrderType> findByCodeIgnoreCase(String code);

    List<OrderType> findAllByOrderByDescriAsc();

    List<OrderType> findByDescriContainingIgnoreCaseOrCodeContainingIgnoreCaseOrderByDescriAsc(String descri, String code);
}