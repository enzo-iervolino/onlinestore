package com.delta.store.order.service;
 

import com.delta.store.order.model.Order;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class OrderEmailServiceImpl implements OrderEmailService {

    private final JavaMailSender mailSender;

    public OrderEmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendOrderConfirmation(String to, Order order, byte[] receiptPdf) throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Conferma ordine #" + order.getId() + " – Online Store");
        helper.setText(buildHtml(order), true);

        if (receiptPdf != null && receiptPdf.length > 0) {
            String filename = "scontrino-ordine-" + order.getId() + ".pdf";
            helper.addAttachment(filename, new ByteArrayResource(receiptPdf));
        }

        mailSender.send(message);
    }

    private String buildHtml(Order order) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:640px;margin:auto">
                <h2>Grazie per il tuo ordine!</h2>
                <p>Abbiamo ricevuto l’ordine <b>#%s</b>.</p>
                <p>Email cliente: <b>%s</b></p>
                <p>Totale ordine: <b>€ %s</b></p>
                <p style="color:#666;font-size:13px;margin-top:20px">
                    In allegato trovi lo scontrino PDF.
                </p>
            </div>
        """.formatted(
                safe(order.getId()),
                safe(order.getCustomerEmail()),
                safe(order.getTotalAmount())
        );
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}