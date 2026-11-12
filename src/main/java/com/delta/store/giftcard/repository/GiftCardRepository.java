package com.delta.store.giftcard.repository;

import com.delta.store.giftcard.model.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from GiftCard g where lower(g.code) = lower(:code)")
    Optional<GiftCard> lockByCode(@org.springframework.data.repository.query.Param("code") String code);

    Optional<GiftCard> findByCodeIgnoreCase(String code);

    List<GiftCard> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<GiftCard> findAllByOrderByCreatedAtDesc();
}
