package com.delta.store.giftcard.service;

import com.delta.store.giftcard.dto.GiftCardIssueForm;
import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardPurchase;
import com.delta.store.user.model.User;

import java.math.BigDecimal;

public interface GiftCardService {

    GiftCard validateGiftCard(String code);

    BigDecimal calculateGiftUsage(GiftCard giftCard, BigDecimal totalBeforeGift);

    GiftCard issueGiftCard(GiftCardIssueForm form, User requester);

    GiftCard issueGiftCardFromPurchase(GiftCardPurchase purchase);

    void consumeGiftCard(GiftCard giftCard,
                         BigDecimal amount,
                         Long orderId);
}
