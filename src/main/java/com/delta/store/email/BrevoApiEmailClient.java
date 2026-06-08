package com.delta.store.email;

import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrevoApiEmailClient {

    private static final Logger log = LoggerFactory.getLogger(BrevoApiEmailClient.class);

    private final RestClient restClient;

    @Value("${app.mail.delivery-mode:${MAIL_DELIVERY_MODE:smtp}}")
    private String deliveryMode;

    @Value("${brevo.api.enabled:${BREVO_API_ENABLED:false}}")
    private boolean brevoApiEnabled;

    @Value("${brevo.api.key:${BREVO_API_KEY:}}")
    private String apiKey;

    @Value("${brevo.api.url:${BREVO_API_URL:https://api.brevo.com/v3/smtp/email}}")
    private String apiUrl;

    @Value("${app.mail.from:${MAIL_FROM:}}")
    private String fromEmail;

    @Value("${app.mail.from-name:${MAIL_FROM_NAME:Online Store}}")
    private String fromName;

    public BrevoApiEmailClient() {
        this.restClient = RestClient.builder().build();
    }

    public boolean isEnabled() {
        String mode = clean(deliveryMode);
        return brevoApiEnabled || "brevo-api".equalsIgnoreCase(mode) || "brevo".equalsIgnoreCase(mode);
    }

    public void sendHtmlEmail(String to,
                              String subject,
                              String htmlContent) throws MessagingException {
        sendHtmlEmail(to, subject, htmlContent, null, null);
    }

    public void sendHtmlEmail(String to,
                              String subject,
                              String htmlContent,
                              byte[] attachment,
                              String attachmentName) throws MessagingException {
        if (!isEnabled()) {
            throw new MessagingException("Brevo API non abilitata. Imposta app.mail.delivery-mode=brevo-api oppure BREVO_API_ENABLED=true.");
        }

        String cleanedApiKey = clean(apiKey);
        String cleanedUrl = clean(apiUrl);
        String cleanedFromEmail = clean(fromEmail);
        String cleanedFromName = clean(fromName);
        String cleanedTo = clean(to);

        if (cleanedApiKey.isBlank()) {
            throw new MessagingException("BREVO_API_KEY mancante: impossibile inviare email tramite Brevo API HTTPS.");
        }
        if (cleanedUrl.isBlank()) {
            throw new MessagingException("BREVO_API_URL mancante.");
        }
        if (cleanedFromEmail.isBlank()) {
            throw new MessagingException("MAIL_FROM mancante: Brevo richiede un mittente verificato.");
        }
        if (cleanedTo.isBlank()) {
            throw new MessagingException("Email destinatario mancante.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", sender(cleanedFromEmail, cleanedFromName));
        payload.put("to", List.of(Map.of("email", cleanedTo)));
        payload.put("subject", subject == null ? "" : subject);
        payload.put("htmlContent", htmlContent == null ? "" : htmlContent);

        if (attachment != null && attachment.length > 0) {
            List<Map<String, String>> attachments = new ArrayList<>();
            Map<String, String> pdf = new LinkedHashMap<>();
            pdf.put("name", clean(attachmentName).isBlank() ? "allegato.pdf" : clean(attachmentName));
            pdf.put("content", Base64.getEncoder().encodeToString(attachment));
            attachments.add(pdf);
            payload.put("attachment", attachments);
        }

        try {
            restClient.post()
                    .uri(cleanedUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("api-key", cleanedApiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Email inviata tramite Brevo API HTTPS a {}", cleanedTo);
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString(StandardCharsets.UTF_8);
            throw new MessagingException(
                    "Brevo API KO: HTTP " + e.getStatusCode().value() + " - " + body,
                    e
            );
        } catch (RestClientException e) {
            throw new MessagingException("Brevo API KO: " + e.getMessage(), e);
        }
    }

    private Map<String, String> sender(String email, String name) {
        Map<String, String> sender = new LinkedHashMap<>();
        sender.put("email", email);
        if (name != null && !name.isBlank()) {
            sender.put("name", name);
        }
        return sender;
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        while ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        return cleaned
                .replace("\r", "")
                .replace("\n", "")
                .trim();
    }
}
