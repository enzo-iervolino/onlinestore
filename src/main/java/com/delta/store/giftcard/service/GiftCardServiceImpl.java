package com.delta.store.giftcard.service;

import com.delta.store.giftcard.dto.GiftCardIssueForm;
import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardPurchase;
import com.delta.store.giftcard.model.GiftCardUsage;
import com.delta.store.giftcard.repository.GiftCardRepository;
import com.delta.store.giftcard.repository.GiftCardUsageRepository;
import com.delta.store.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class GiftCardServiceImpl implements GiftCardService {

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

    private final GiftCardRepository giftCardRepository;
    private final GiftCardUsageRepository giftCardUsageRepository;
    private final SecureRandom secureRandom = new SecureRandom();

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

        if (giftCard.getExpirationDate() != null
                && giftCard.getExpirationDate().isBefore(LocalDate.now())) {
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

        if (totalBeforeGift == null
                || totalBeforeGift.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return giftCard.getRemainingAmount().min(totalBeforeGift);
    }

    @Override
    @Transactional
    public GiftCard issueGiftCard(GiftCardIssueForm form, User requester) {
        if (requester == null || requester.getId() == null) {
            throw new IllegalStateException("Per emettere una gift card devi essere autenticato.");
        }

        BigDecimal amount = resolveAmount(form).setScale(2, RoundingMode.HALF_UP);
        int validityMonths = resolveValidityMonths(form);

        GiftCard giftCard = new GiftCard();
        giftCard.setCode(generateUniqueCode(amount));
        giftCard.setCustomerId(requester.getId());
        giftCard.setInitialAmount(amount);
        giftCard.setRemainingAmount(amount);
        giftCard.setActive(true);
        giftCard.setUsed(false);
        giftCard.setValidityMonths(validityMonths);
        giftCard.setExpirationDate(LocalDate.now().plusMonths(validityMonths));
        giftCard.setRecipientFirstName(clean(form == null ? null : form.getRecipientFirstName(), 80));
        giftCard.setRecipientLastName(clean(form == null ? null : form.getRecipientLastName(), 80));
        giftCard.setRecipientEmail(validateAndCleanEmail(form == null ? null : form.getRecipientEmail()));
        giftCard.setPersonalMessage(clean(form == null ? null : form.getPersonalMessage(), 1000));

        return giftCardRepository.save(giftCard);
    }


    @Override
    @Transactional
    public GiftCard issueGiftCardFromPurchase(GiftCardPurchase purchase) {
        if (purchase == null || purchase.getId() == null) {
            throw new IllegalArgumentException("Richiesta gift card mancante.");
        }
        if (purchase.getCustomerId() == null) {
            throw new IllegalStateException("Cliente richiedente mancante per la gift card.");
        }

        BigDecimal amount = purchase.getAmount() == null
                ? null
                : purchase.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Importo gift card non valido.");
        }

        int validityMonths = purchase.getValidityMonths() == null
                ? DEFAULT_VALIDITY_MONTHS
                : purchase.getValidityMonths();

        if (validityMonths <= 0) {
            validityMonths = DEFAULT_VALIDITY_MONTHS;
        }
        if (validityMonths > MAX_VALIDITY_MONTHS) {
            throw new IllegalArgumentException("La validità della gift card non può superare 12 mesi.");
        }

        GiftCard giftCard = new GiftCard();
        giftCard.setCode(generateUniqueCode(amount));
        giftCard.setCustomerId(purchase.getCustomerId());
        giftCard.setInitialAmount(amount);
        giftCard.setRemainingAmount(amount);
        giftCard.setActive(true);
        giftCard.setUsed(false);
        giftCard.setValidityMonths(validityMonths);
        giftCard.setExpirationDate(LocalDate.now().plusMonths(validityMonths));
        giftCard.setRecipientFirstName(clean(purchase.getRecipientFirstName(), 80));
        giftCard.setRecipientLastName(clean(purchase.getRecipientLastName(), 80));
        giftCard.setRecipientEmail(validateAndCleanEmail(purchase.getRecipientEmail()));
        giftCard.setPersonalMessage(clean(purchase.getPersonalMessage(), 1000));

        return giftCardRepository.save(giftCard);
    }

    @Override
    @Transactional
    public void consumeGiftCard(GiftCard giftCard,
                                BigDecimal amount,
                                Long orderId) {

        GiftCard managed = giftCardRepository
                .lockByCode(giftCard.getCode())
                .orElseThrow(() ->
                        new IllegalStateException("GiftCard non trovata"));

        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) throw new IllegalArgumentException("Importo gift card non valido");
        if (!managed.isActive() || (managed.getExpirationDate() != null && managed.getExpirationDate().isBefore(LocalDate.now()))) throw new IllegalStateException("Gift card non disponibile");
        BigDecimal remaining = managed.getRemainingAmount();

        if (amount.compareTo(remaining) > 0) {
            throw new IllegalStateException("Credito insufficiente");
        }

        managed.setRemainingAmount(remaining.subtract(amount));

        if (managed.getRemainingAmount()
                .compareTo(BigDecimal.ZERO) == 0) {
            managed.setUsed(true);
        }

        giftCardRepository.save(managed);

        GiftCardUsage usage = new GiftCardUsage();
        usage.setGiftCard(managed);
        usage.setOrderId(orderId);
        usage.setAmountUsed(amount);

        giftCardUsageRepository.save(usage);
    }

    private BigDecimal resolveAmount(GiftCardIssueForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Dati gift card mancanti.");
        }

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
        Integer months = form == null ? null : form.getValidityMonths();
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

    private String generateUniqueCode(BigDecimal amount) {
        for (int attempt = 0; attempt < 10; attempt++) {
            byte[] bytes = new byte[15];
            secureRandom.nextBytes(bytes);
            String code = "GIFT-" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (giftCardRepository.findByCodeIgnoreCase(code).isEmpty()) {
                return code;
            }
        }

        throw new IllegalStateException("Impossibile generare un codice gift card univoco. Riprova.");
    }

    private String formatAmountForCode(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return normalized.toPlainString();
        }
        return amount.setScale(2, RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', '_');
    }

    private String uniqueNumber() {
        long millis = System.currentTimeMillis();
        int suffix = secureRandom.nextInt(90) + 10;
        return String.valueOf(millis) + suffix;
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
