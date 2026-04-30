package com.delta.store.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // =========================
    // RESET PASSWORD (NON TOCCATO)
    // =========================
    @Override
    public void sendPasswordResetEmail(String to, String resetLink) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Recupero password – Online Store");
        helper.setText(buildResetHtml(resetLink), true);

        mailSender.send(message);
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
        """.formatted(resetLink);
    }

    // =========================
    // NUOVO METODO ORDINE
    // =========================
    @Override
    public void sendOrderConfirmationEmail(String to,
                                           String orderNumber,
                                           String total,
                                           String paymentMethod,
                                           byte[] pdfAttachment) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Conferma Ordine #" + orderNumber);
        helper.setText(buildOrderHtml(orderNumber, total, paymentMethod), true);

        helper.addAttachment(
                "scontrino-" + orderNumber + ".pdf",
                new ByteArrayResource(pdfAttachment)
        );

        mailSender.send(message);
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
        """.formatted(orderNumber, total, paymentMethod);
    }
}
