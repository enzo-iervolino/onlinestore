package com.delta.store.giftcard.repository;

import com.delta.store.giftcard.model.GiftCardPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GiftCardPurchaseRepository extends JpaRepository<GiftCardPurchase, Long> {

    Optional<GiftCardPurchase> findByStripeSessionId(String stripeSessionId);

    Optional<GiftCardPurchase> findByPaypalOrderId(String paypalOrderId);

    Optional<GiftCardPurchase> findByGiftCard_Id(Long giftCardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GiftCardPurchase p left join fetch p.giftCard where p.id = :id")
    Optional<GiftCardPurchase> findByIdForUpdate(@Param("id") Long id);
}
