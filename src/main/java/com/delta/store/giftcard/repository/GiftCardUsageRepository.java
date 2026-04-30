package com.delta.store.giftcard.repository;

import com.delta.store.giftcard.model.GiftCardUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GiftCardUsageRepository
        extends JpaRepository<GiftCardUsage, Long> {

    List<GiftCardUsage> findAllByOrderByUsedAtDesc();

    List<GiftCardUsage>
    findByGiftCard_CodeContainingIgnoreCaseOrderByUsedAtDesc(String code);

    List<GiftCardUsage>
    findByUsedAtBetweenOrderByUsedAtDesc(LocalDateTime from,
                                        LocalDateTime to);
}
