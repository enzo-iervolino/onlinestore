package com.delta.store.order.service;

import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderTempData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OrderService {

    Optional<Order> findById(Long id);

    /**
     * Ordine pagato interamente con GiftCard (nessun gateway esterno).
     */
    Order createOrderPaidByGiftcard(OrderTempData data);

    /**
     * Ordine pagato con Stripe (creato dal webhook).
     */
    Order createOrderPaidByStripe(OrderTempData data, String stripeSessionId);

    /**
     * Ordine pagato con PayPal (creato SOLO dopo capture COMPLETED).
     */
    Order createOrderPaidByPayPal(OrderTempData data, String payPalOrderId);

    List<Order> SelAll();

    Optional<Order> SelById(long id);

    void Delete(Order order);

    Order OrderInsert(Order order);

    Optional<Order> findByStripeSessionId(String stripeSessionId);

    Optional<Order> findBySessionId(String sessionId);

    List<Order> findClientOrders(String email);

    Page<Order> search(LocalDate startDate,
                       LocalDate endDate,
                       String email,
                       String status,
                       String userType,
                       Pageable pageable);

    Optional<Order> findByIdWithItems(Long id);

    List<Order> findClientOrdersByUserId(Long userId);

    void deleteById(Long id);

    void updateStatus(Long id, String status);
}