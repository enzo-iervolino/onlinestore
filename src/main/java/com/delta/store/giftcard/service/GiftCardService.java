package com.delta.store.giftcard.service;

import com.delta.store.giftcard.model.GiftCard;

import java.math.BigDecimal;

public interface GiftCardService {

    GiftCard validateGiftCard(String code);

    BigDecimal calculateGiftUsage(GiftCard giftCard, BigDecimal totalBeforeGift);

    void consumeGiftCard(GiftCard giftCard,
                         BigDecimal amount,
                         Long orderId);
}
