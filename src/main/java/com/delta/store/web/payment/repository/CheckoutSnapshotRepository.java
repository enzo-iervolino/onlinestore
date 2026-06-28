package com.delta.store.web.payment.repository;

import com.delta.store.web.payment.model.CheckoutSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutSnapshotRepository extends JpaRepository<CheckoutSnapshot, Long> {

    Optional<CheckoutSnapshot> findByStripeSessionId(String stripeSessionId);

    void deleteByStripeSessionId(String stripeSessionId);

    Optional<CheckoutSnapshot> findByPaypalOrderId(String paypalOrderId);

    void deleteByPaypalOrderId(String paypalOrderId);
}
