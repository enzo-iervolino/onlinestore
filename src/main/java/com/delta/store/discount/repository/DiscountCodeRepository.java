package com.delta.store.discount.repository;

import com.delta.store.discount.model.DiscountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

    Optional<DiscountCode> findByCodeIgnoreCaseAndStatus(
            String code,
            DiscountCode.Status status
    );

    default boolean isExpired(DiscountCode dc) {
        return dc.getExpiresAt() != null
                && dc.getExpiresAt().isBefore(LocalDateTime.now());
    }
}

