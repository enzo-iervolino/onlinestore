package com.delta.store.giftcard.service;

import com.delta.store.email.EmailService;
import com.delta.store.giftcard.model.GiftCard;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class GiftCardNotificationService {

    private final EmailService emailService;

    public GiftCardNotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    public String sendEmailIfRecipientPresent(GiftCard giftCard) {
        if (giftCard == null
                || giftCard.getRecipientEmail() == null
                || giftCard.getRecipientEmail().isBlank()) {
            return null;
        }

        try {
            emailService.sendGiftCardEmail(
                    giftCard.getRecipientEmail(),
                    buildRecipientName(giftCard),
                    giftCard.getCode(),
                    formatEuro(giftCard.getInitialAmount()),
                    giftCard.getExpirationDate(),
                    giftCard.getPersonalMessage()
            );
            return null;
        } catch (Exception ex) {
            return "Gift card emessa correttamente, ma l'e-mail al destinatario non è stata inviata: " + ex.getMessage();
        }
    }

    private String buildRecipientName(GiftCard giftCard) {
        String firstName = giftCard.getRecipientFirstName() == null ? "" : giftCard.getRecipientFirstName().trim();
        String lastName = giftCard.getRecipientLastName() == null ? "" : giftCard.getRecipientLastName().trim();
        return (firstName + " " + lastName).trim();
    }

    private String formatEuro(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
