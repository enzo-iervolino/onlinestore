package com.delta.store.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:${MAIL_FROM:}}")
    private String configuredFrom;

    @Value("${app.mail.from-name:${MAIL_FROM_NAME:Online Store}}")
    private String configuredFromName;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) throws MessagingException {
        if (to == null || to.isBlank()) {
            throw new MessagingException("Email destinatario mancante");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        applyFrom(helper);
        helper.setTo(to.trim());
        helper.setSubject("Recupero password – Online Store");
        helper.setText(buildResetHtml(resetLink), true);

        mailSender.send(message);
    }

    @Override
    public void sendOrderConfirmationEmail(String to,
                                           String orderNumber,
                                           String total,
                                           String paymentMethod,
                                           byte[] pdfAttachment) throws MessagingException {
        if (to == null || to.isBlank()) {
            throw new MessagingException("Email destinatario mancante");
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        applyFrom(helper);
        helper.setTo(to.trim());
        helper.setSubject("Conferma Ordine #" + safe(orderNumber));
        helper.setText(buildOrderHtml(orderNumber, total, paymentMethod), true);

        if (pdfAttachment != null && pdfAttachment.length > 0) {
            helper.addAttachment(
                    "scontrino-" + safe(orderNumber) + ".pdf",
                    new ByteArrayResource(pdfAttachment)
            );
        }

        mailSender.send(message);
    }

    private void applyFrom(MimeMessageHelper helper) throws MessagingException {
        String from = resolveFromAddress();
        if (from == null || from.isBlank()) {
            return;
        }

        String fromName = configuredFromName == null ? "" : configuredFromName.trim();
        if (fromName.isBlank()) {
            helper.setFrom(from);
            return;
        }

        try {
            helper.setFrom(from, fromName);
        } catch (UnsupportedEncodingException e) {
            helper.setFrom(from);
        }
    }

    private String resolveFromAddress() {
        if (configuredFrom != null && !configuredFrom.isBlank()) {
            return configuredFrom.trim();
        }
        if (mailUsername != null && !mailUsername.isBlank()) {
            return mailUsername.trim();
        }
        return null;
    }

    private String buildResetHtml(String resetLink) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
                <h2>Recupero password</h2>
                <p>Hai richiesto il reset della password.</p>
                <p>Clicca sul pulsante qui sotto per impostarne una nuova:</p>

                <div style="text-align:center;margin:30px 0">
                    <a href="%s"
                       style="background:#0d6efd;color:#fff;
                              padding:12px 20px;
                              text-decoration:none;
                              border-radius:6px;
                              display:inline-block">
                        Reimposta password
                    </a>
                </div>

                <p style="color:#666;font-size:13px">
                    Il link è valido per 30 minuti.
                    Se non hai richiesto tu questa operazione, ignora l’email.
                </p>
            </div>
        """.formatted(safe(resetLink));
    }

    private String buildOrderHtml(String orderNumber,
                                  String total,
                                  String paymentMethod) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
                <h2>Ordine Confermato 🎉</h2>

                <p>Grazie per il tuo acquisto!</p>

                <p><strong>Numero ordine:</strong> %s</p>
                <p><strong>Totale pagato:</strong> € %s</p>
                <p><strong>Metodo pagamento:</strong> %s</p>

                <p style="margin-top:30px;color:#666;font-size:13px">
                    In allegato trovi lo scontrino PDF del tuo ordine.
                </p>
            </div>
        """.formatted(safe(orderNumber), safe(total), safe(paymentMethod));
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
