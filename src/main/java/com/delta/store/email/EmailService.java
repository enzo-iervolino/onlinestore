
package com.delta.store.email;

import jakarta.mail.MessagingException;

public interface EmailService {

    void sendPasswordResetEmail(String to, String resetLink) throws MessagingException;

    void sendOrderConfirmationEmail(String to,
                                    String orderNumber,
                                    String total,
                                    String paymentMethod,
                                    byte[] pdfAttachment) throws MessagingException;
}
