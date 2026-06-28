package com.delta.store.giftcard.service;

import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardUsage;
import com.delta.store.giftcard.repository.GiftCardRepository;
import com.delta.store.giftcard.repository.GiftCardUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class GiftCardServiceImpl implements GiftCardService {

    private final GiftCardRepository giftCardRepository;
    private final GiftCardUsageRepository giftCardUsageRepository;

    public GiftCardServiceImpl(GiftCardRepository giftCardRepository,
                               GiftCardUsageRepository giftCardUsageRepository) {
        this.giftCardRepository = giftCardRepository;
        this.giftCardUsageRepository = giftCardUsageRepository;
    }

    @Override
    public GiftCard validateGiftCard(String code) {

        GiftCard giftCard = giftCardRepository
                .findByCodeIgnoreCase(code)
                .orElseThrow(() -> new IllegalArgumentException("Gift card non valida"));

        if (!giftCard.isActive()) {
            throw new IllegalStateException("Gift card disattivata");
        }

        if (giftCard.getExpirationDate() != null &&
            giftCard.getExpirationDate().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Gift card scaduta");
        }

        if (giftCard.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Credito esaurito");
        }

        return giftCard;
    }

    @Override
    public BigDecimal calculateGiftUsage(GiftCard giftCard,
                                         BigDecimal totalBeforeGift) {

        if (totalBeforeGift == null ||
            totalBeforeGift.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return giftCard.getRemainingAmount().min(totalBeforeGift);
    }

    @Override
    @Transactional
    public void consumeGiftCard(GiftCard giftCard,
                                BigDecimal amount,
                                Long orderId) {

        GiftCard managed = giftCardRepository
                .findById(giftCard.getId())
                .orElseThrow(() ->
                        new IllegalStateException("GiftCard non trovata"));

        BigDecimal remaining = managed.getRemainingAmount();

        if (amount.compareTo(remaining) > 0) {
            throw new IllegalStateException("Credito insufficiente");
        }

        // 🔹 Aggiorno saldo
        managed.setRemainingAmount(remaining.subtract(amount));

        if (managed.getRemainingAmount()
                .compareTo(BigDecimal.ZERO) == 0) {
            managed.setUsed(true);
        }

        giftCardRepository.save(managed);

        // 🔹 Registro movimento
        GiftCardUsage usage = new GiftCardUsage();
        usage.setGiftCard(managed);
        usage.setOrderId(orderId);
        usage.setAmountUsed(amount);

        giftCardUsageRepository.save(usage);
    }

}
