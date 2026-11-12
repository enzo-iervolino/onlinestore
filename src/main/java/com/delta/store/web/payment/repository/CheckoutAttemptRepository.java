package com.delta.store.web.payment.repository;

import com.delta.store.web.payment.model.CheckoutAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CheckoutAttempt a where a.id = :id")
    Optional<CheckoutAttempt> lock(@Param("id") String id);
    Optional<CheckoutAttempt> findByProviderReference(String reference);
    Optional<CheckoutAttempt> findByActiveOwner(String owner);
    Optional<CheckoutAttempt> findByOrderId(Long orderId);
    @Query("select a from CheckoutAttempt a where a.state in ('PREPARED','PENDING','CAPTURING','PAID_REVIEW') order by coalesce(a.lastCheckedAt,a.createdAt)")
    List<CheckoutAttempt> pending(org.springframework.data.domain.Pageable page);
}
