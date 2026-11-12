package com.delta.store.web.payment.repository;

import com.delta.store.web.payment.model.CheckoutSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutSnapshotRepository extends JpaRepository<CheckoutSnapshot, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from CheckoutSnapshot s where s.stripeSessionId = :id")
    Optional<CheckoutSnapshot> lockStripe(@org.springframework.data.repository.query.Param("id") String id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from CheckoutSnapshot s where s.paypalOrderId = :id")
    Optional<CheckoutSnapshot> lockPayPal(@org.springframework.data.repository.query.Param("id") String id);
    Optional<CheckoutSnapshot> findByStripeSessionId(String stripeSessionId);

    void deleteByStripeSessionId(String stripeSessionId);

    Optional<CheckoutSnapshot> findByPaypalOrderId(String paypalOrderId);

    void deleteByPaypalOrderId(String paypalOrderId);
}
