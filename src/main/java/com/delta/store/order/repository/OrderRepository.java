package com.delta.store.order.repository;

import com.delta.store.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByStripeSessionId(String stripeSessionId);

    Optional<Order> findBySessionId(String sessionId);

    @Query("""
           select distinct o
           from Order o
           left join fetch o.items
           left join fetch o.typeOrder
           where o.id = :id
           """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}