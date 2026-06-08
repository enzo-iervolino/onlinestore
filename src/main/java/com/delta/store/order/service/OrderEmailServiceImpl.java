package com.delta.store.order.service;

import com.delta.store.email.BrevoApiEmailClient;
import com.delta.store.order.model.Order;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class OrderEmailServiceImpl implements OrderEmailService {

    private final JavaMailSender mailSender;
    private final BrevoApiEmailClient brevoApiEmailClient;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:${MAIL_FROM:}}")
    private String configuredFrom;

    @Value("${app.mail.from-name:${MAIL_FROM_NAME:Online Store}}")
    private String configuredFromName;

    public OrderEmailServiceImpl(JavaMailSender mailSender,
                                 BrevoApiEmailClient brevoApiEmailClient) {
        this.mailSender = mailSender;
        this.brevoApiEmailClient = brevoApiEmailClient;
    }

    @Override
    public void sendOrderConfirmation(String to, Order order, byte[] receiptPdf) throws MessagingException {
        if (to == null || to.isBlank()) {
            throw new MessagingException("Email destinatario mancante");
        }
        if (order == null) {
            throw new MessagingException("Ordine mancante");
        }

        String subject = "Conferma ordine #" + order.getId() + " – Online Store";
        String html = buildHtml(order);
        String filename = "scontrino-ordine-" + order.getId() + ".pdf";

        if (brevoApiEmailClient.isEnabled()) {
            brevoApiEmailClient.sendHtmlEmail(to.trim(), subject, html, receiptPdf, filename);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        applyFrom(helper);
        helper.setTo(to.trim());
        helper.setSubject(subject);
        helper.setText(html, true);

        if (receiptPdf != null && receiptPdf.length > 0) {
            helper.addAttachment(filename, new ByteArrayResource(receiptPdf));
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

    private String buildHtml(Order order) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:640px;margin:auto">
                <h2>Grazie per il tuo ordine!</h2>
                <p>Abbiamo ricevuto l’ordine <b>#%s</b>.</p>
                <p>Email cliente: <b>%s</b></p>
                <p>Totale ordine: <b>€ %s</b></p>
                <p style="color:#666;font-size:13px;margin-top:20px">
                    %s
                </p>
            </div>
        """.formatted(
                safe(order.getId()),
                safe(order.getCustomerEmail()),
                safe(order.getTotalAmount()),
                "In allegato trovi lo scontrino PDF."
        );
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
