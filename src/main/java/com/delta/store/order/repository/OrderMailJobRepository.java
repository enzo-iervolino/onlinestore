package com.delta.store.order.repository;
import com.delta.store.order.model.OrderMailJob;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
public interface OrderMailJobRepository extends JpaRepository<OrderMailJob,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from OrderMailJob j where j.orderId=:id")
    Optional<OrderMailJob> lock(@org.springframework.data.repository.query.Param("id") Long id);
    List<OrderMailJob> findTop20ByDeliveredFalseAndNextAttemptBeforeOrderByNextAttemptAsc(LocalDateTime now);
}
