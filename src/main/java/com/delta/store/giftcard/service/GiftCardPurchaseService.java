package com.delta.store.giftcard.service;

import com.delta.store.giftcard.dto.GiftCardIssueForm;
import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardPaymentMethod;
import com.delta.store.giftcard.model.GiftCardPurchase;
import com.delta.store.giftcard.model.GiftCardPurchaseStatus;
import com.delta.store.giftcard.repository.GiftCardPurchaseRepository;
import com.delta.store.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class GiftCardPurchaseService {

    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000.00");
    private static final int DEFAULT_VALIDITY_MONTHS = 6;
    private static final int MAX_VALIDITY_MONTHS = 12;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final List<String> FIXED_AMOUNTS = List.of(
            "25.00",
            "50.00",
            "100.00",
            "150.00",
            "200.00",
            "300.00",
            "400.00",
            "500.00"
    );

    private final GiftCardPurchaseRepository purchaseRepository;
    private final GiftCardService giftCardService;

    public GiftCardPurchaseService(GiftCardPurchaseRepository purchaseRepository,
                                   GiftCardService giftCardService) {
        this.purchaseRepository = purchaseRepository;
        this.giftCardService = giftCardService;
    }

    @Transactional
    public GiftCardPurchase createPendingPurchase(GiftCardIssueForm form, User requester) {
        if (requester == null || requester.getId() == null) {
            throw new IllegalStateException("Per acquistare una gift card devi essere autenticato.");
        }
        if (form == null) {
            throw new IllegalArgumentException("Dati gift card mancanti.");
        }

        GiftCardPaymentMethod paymentMethod = resolvePaymentMethod(form.getPaymentMethod());
        BigDecimal amount = resolveAmount(form).setScale(2, RoundingMode.HALF_UP);
        int validityMonths = resolveValidityMonths(form);

        GiftCardPurchase purchase = new GiftCardPurchase();
        purchase.setCustomerId(requester.getId());
        purchase.setAmount(amount);
        purchase.setValidityMonths(validityMonths);
        purchase.setRecipientFirstName(clean(form.getRecipientFirstName(), 80));
        purchase.setRecipientLastName(clean(form.getRecipientLastName(), 80));
        purchase.setRecipientEmail(validateAndCleanEmail(form.getRecipientEmail()));
        purchase.setPersonalMessage(clean(form.getPersonalMessage(), 1000));
        purchase.setPaymentMethod(paymentMethod);
        purchase.setStatus(GiftCardPurchaseStatus.PENDING);
        purchase.setExpiresAt(LocalDateTime.now().plusHours(2));

        return purchaseRepository.save(purchase);
    }

    @Transactional
    public void attachStripeSession(Long purchaseId, String stripeSessionId) {
        GiftCardPurchase purchase = getManagedPurchase(purchaseId);
        purchase.setStripeSessionId(clean(stripeSessionId, 160));
        purchaseRepository.save(purchase);
    }

    @Transactional
    public void attachPayPalOrder(Long purchaseId, String paypalOrderId) {
        GiftCardPurchase purchase = getManagedPurchase(purchaseId);
        purchase.setPaypalOrderId(clean(paypalOrderId, 160));
        purchaseRepository.save(purchase);
    }

    public Optional<GiftCardPurchase> findByStripeSessionId(String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            return Optional.empty();
        }
        return purchaseRepository.findByStripeSessionId(stripeSessionId);
    }

    public Optional<GiftCardPurchase> findByPayPalOrderId(String paypalOrderId) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            return Optional.empty();
        }
        return purchaseRepository.findByPaypalOrderId(paypalOrderId);
    }

    @Transactional
    public GiftCardPaymentResult completeStripePayment(Long purchaseId,
                                                       String stripeSessionId,
                                                       BigDecimal paidAmount) {
        GiftCardPaymentResult result = completePaidPurchase(purchaseId, stripeSessionId, paidAmount);
        if (result.purchase().getStripeSessionId() == null || result.purchase().getStripeSessionId().isBlank()) {
            result.purchase().setStripeSessionId(clean(stripeSessionId, 160));
            purchaseRepository.save(result.purchase());
        }
        return result;
    }

    @Transactional
    public GiftCardPaymentResult completePayPalPayment(String paypalOrderId,
                                                       BigDecimal paidAmount) {
        GiftCardPurchase purchase = purchaseRepository.findByPaypalOrderId(paypalOrderId)
                .orElseThrow(() -> new IllegalStateException("Richiesta gift card PayPal non trovata."));
        return completePaidPurchase(purchase.getId(), paypalOrderId, paidAmount);
    }

    @Transactional
    public void markCancelled(Long purchaseId) {
        if (purchaseId == null) {
            return;
        }

        GiftCardPurchase purchase = getManagedPurchase(purchaseId);
        if (purchase.getStatus() == GiftCardPurchaseStatus.PAID) {
            return;
        }
        purchase.setStatus(GiftCardPurchaseStatus.CANCELLED);
        purchaseRepository.save(purchase);
    }

    @Transactional
    public void markFailed(Long purchaseId) {
        if (purchaseId == null) {
            return;
        }

        GiftCardPurchase purchase = getManagedPurchase(purchaseId);
        if (purchase.getStatus() == GiftCardPurchaseStatus.PAID) {
            return;
        }
        purchase.setStatus(GiftCardPurchaseStatus.FAILED);
        purchaseRepository.save(purchase);
    }

    private GiftCardPaymentResult completePaidPurchase(Long purchaseId,
                                                       String providerReference,
                                                       BigDecimal paidAmount) {
        GiftCardPurchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new IllegalStateException("Richiesta gift card non trovata."));

        if (purchase.getGiftCard() != null) {
            purchase.setStatus(GiftCardPurchaseStatus.PAID);
            if (purchase.getPaidAt() == null) {
                purchase.setPaidAt(LocalDateTime.now());
            }
            purchase.setProviderReference(clean(providerReference, 180));
            purchaseRepository.save(purchase);
            return new GiftCardPaymentResult(purchase, purchase.getGiftCard(), false);
        }

        BigDecimal expectedAmount = purchase.getAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : purchase.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal normalizedPaidAmount = paidAmount == null
                ? expectedAmount
                : paidAmount.setScale(2, RoundingMode.HALF_UP);

        if (normalizedPaidAmount.compareTo(expectedAmount) < 0) {
            throw new IllegalStateException("Pagamento gift card incompleto: importo pagato "
                    + normalizedPaidAmount + " inferiore all'importo richiesto " + expectedAmount + ".");
        }

        GiftCard giftCard = giftCardService.issueGiftCardFromPurchase(purchase);

        purchase.setGiftCard(giftCard);
        purchase.setStatus(GiftCardPurchaseStatus.PAID);
        purchase.setPaidAt(LocalDateTime.now());
        purchase.setProviderReference(clean(providerReference, 180));
        purchaseRepository.save(purchase);

        return new GiftCardPaymentResult(purchase, giftCard, true);
    }

    private GiftCardPurchase getManagedPurchase(Long purchaseId) {
        if (purchaseId == null) {
            throw new IllegalArgumentException("Richiesta gift card mancante.");
        }
        return purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new IllegalStateException("Richiesta gift card non trovata."));
    }

    private GiftCardPaymentMethod resolvePaymentMethod(String value) {
        String cleaned = clean(value, 30);
        if (cleaned == null) {
            return GiftCardPaymentMethod.STRIPE;
        }

        try {
            return GiftCardPaymentMethod.valueOf(cleaned.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Seleziona una modalità di pagamento valida.");
        }
    }

    private BigDecimal resolveAmount(GiftCardIssueForm form) {
        String selectedAmount = clean(form.getSelectedAmount(), 20);
        BigDecimal amount;

        if ("OTHER".equalsIgnoreCase(selectedAmount)) {
            amount = form.getCustomAmount();
        } else {
            if (!FIXED_AMOUNTS.contains(selectedAmount)) {
                throw new IllegalArgumentException("Seleziona un importo gift card valido.");
            }
            amount = new BigDecimal(selectedAmount);
        }

        if (amount == null) {
            throw new IllegalArgumentException("Inserisci un importo gift card valido.");
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("L'importo minimo della gift card è € 1,00.");
        }

        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("L'importo massimo della gift card è € 1000,00.");
        }

        return amount;
    }

    private int resolveValidityMonths(GiftCardIssueForm form) {
        Integer months = form.getValidityMonths();
        int validityMonths = months == null ? DEFAULT_VALIDITY_MONTHS : months;

        if (validityMonths <= 0) {
            validityMonths = DEFAULT_VALIDITY_MONTHS;
        }

        if (validityMonths > MAX_VALIDITY_MONTHS) {
            throw new IllegalArgumentException("La validità della gift card non può superare 12 mesi.");
        }

        return validityMonths;
    }

    private String validateAndCleanEmail(String email) {
        String cleaned = clean(email, 160);
        if (cleaned == null) {
            return null;
        }

        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Inserisci un indirizzo e-mail destinatario valido.");
        }

        return normalized;
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();
        if (cleaned.isBlank()) {
            return null;
        }

        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }

        return cleaned;
    }
}
