package com.delta.store.email;

import jakarta.mail.MessagingException;

import java.time.LocalDate;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink) throws MessagingException;

    void sendOrderConfirmationEmail(String to,
                                    String orderNumber,
                                    String total,
                                    String paymentMethod,
                                    byte[] pdfAttachment) throws MessagingException;

    void sendGiftCardEmail(String to,
                           String recipientName,
                           String code,
                           String amount,
                           LocalDate expirationDate,
                           String personalMessage) throws MessagingException;
}
