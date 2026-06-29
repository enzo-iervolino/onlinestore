package com.delta.store.giftcard.service;

import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardPurchase;

public record GiftCardPaymentResult(GiftCardPurchase purchase,
                                    GiftCard giftCard,
                                    boolean newlyIssued) {
}
