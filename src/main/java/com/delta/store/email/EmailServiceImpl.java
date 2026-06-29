package com.delta.store.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmailServiceImpl implements EmailService {

    private static final DateTimeFormatter ITALIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final BrevoApiEmailClient brevoApiEmailClient;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from:${MAIL_FROM:}}")
    private String configuredFrom;

    @Value("${app.mail.from-name:${MAIL_FROM_NAME:Online Store}}")
    private String configuredFromName;

    public EmailServiceImpl(JavaMailSender mailSender,
                            BrevoApiEmailClient brevoApiEmailClient) {
        this.mailSender = mailSender;
        this.brevoApiEmailClient = brevoApiEmailClient;
    }

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) throws MessagingException {
        if (to == null || to.isBlank()) {
            throw new MessagingException("Email destinatario mancante");
        }

        String subject = "Recupero password – Online Store";
        String html = buildResetHtml(resetLink);

        if (brevoApiEmailClient.isEnabled()) {
            brevoApiEmailClient.sendHtmlEmail(to.trim(), subject, html);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        applyFrom(helper);
        helper.setTo(to.trim());
        helper.setSubject(subject);
        helper.setText(html, true);

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

        String subject = "Conferma Ordine #" + safe(orderNumber);
        String html = buildOrderHtml(orderNumber, total, paymentMethod);
        String filename = "scontrino-" + safe(orderNumber) + ".pdf";

        if (brevoApiEmailClient.isEnabled()) {
            brevoApiEmailClient.sendHtmlEmail(to.trim(), subject, html, pdfAttachment, filename);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        applyFrom(helper);
        helper.setTo(to.trim());
        helper.setSubject(subject);
        helper.setText(html, true);

        if (pdfAttachment != null && pdfAttachment.length > 0) {
            helper.addAttachment(filename, new ByteArrayResource(pdfAttachment));
        }

        mailSender.send(message);
    }

    @Override
    public void sendGiftCardEmail(String to,
                                  String recipientName,
                                  String code,
                                  String amount,
                                  LocalDate expirationDate,
                                  String personalMessage) throws MessagingException {
        if (to == null || to.isBlank()) {
            throw new MessagingException("Email destinatario mancante");
        }

        String subject = "La tua Gift Card Online Store";
        String html = buildGiftCardHtml(recipientName, code, amount, expirationDate, personalMessage);

        if (brevoApiEmailClient.isEnabled()) {
            brevoApiEmailClient.sendHtmlEmail(to.trim(), subject, html);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        applyFrom(helper);
        helper.setTo(to.trim());
        helper.setSubject(subject);
        helper.setText(html, true);

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

    private String buildGiftCardHtml(String recipientName,
                                     String code,
                                     String amount,
                                     LocalDate expirationDate,
                                     String personalMessage) {
        String name = recipientName == null || recipientName.isBlank() ? "" : "Ciao " + escapeHtml(recipientName.trim()) + ",";
        String messageBlock = personalMessage == null || personalMessage.isBlank()
                ? ""
                : """
                    <div style="background:#f7f7f7;border-left:4px solid #111;padding:14px 16px;margin:22px 0">
                        <strong>Messaggio:</strong><br>
                        %s
                    </div>
                """.formatted(escapeHtml(personalMessage).replace("\n", "<br>"));

        return """
            <div style="font-family:Arial,sans-serif;max-width:620px;margin:auto;color:#111">
                <div style="background:#111;color:#fff;border-radius:18px 18px 0 0;padding:30px;text-align:center">
                    <div style="font-size:34px;font-weight:800;letter-spacing:1px">ONLINE STORE</div>
                    <div style="font-size:18px;letter-spacing:2px;margin-top:6px">GIFT CARD ONLINE</div>
                </div>
                <div style="border:1px solid #eee;border-top:0;border-radius:0 0 18px 18px;padding:26px;background:#fff">
                    <p>%s</p>
                    <p>Hai ricevuto una gift card da utilizzare per i tuoi acquisti online.</p>
                    %s
                    <p style="font-size:18px"><strong>Importo:</strong> € %s</p>
                    <p style="font-size:18px"><strong>Codice:</strong> <span style="font-family:monospace;background:#f2f2f2;padding:6px 8px;border-radius:6px">%s</span></p>
                    <p><strong>Scadenza:</strong> %s</p>
                    <p style="color:#666;font-size:13px;margin-top:24px">
                        Conserva questo codice: potrai inserirlo nella sezione gift card durante il checkout.
                    </p>
                </div>
            </div>
        """.formatted(
                name,
                messageBlock,
                safe(amount),
                escapeHtml(code),
                expirationDate == null ? "" : expirationDate.format(ITALIAN_DATE)
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
