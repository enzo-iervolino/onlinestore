package com.delta.store.web.payment.processor;

import com.delta.store.order.model.OrderTempData;
import com.delta.store.web.payment.CheckoutPaymentContext;
import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.web.payment.PaymentMethod;
import com.delta.store.web.payment.PaymentProcessor;
import com.delta.store.web.payment.service.CheckoutSnapshotService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StripePaymentProcessor implements PaymentProcessor {

    private final CheckoutSnapshotService snapshotService;

    @Value("${stripe.secret-key:${stripe.secret.key:}}")
    private String stripeSecretKey;

    @Value("${app.base-url:http://localhost:8081}")
    private String appBaseUrl;

    @Value("${app.payment.success-url:http://localhost:8081/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}}")
    private String successUrl;

    @Value("${app.payment.cancel-url:http://localhost:8081/checkout/stripe/cancel}")
    private String cancelUrl;

    public StripePaymentProcessor(CheckoutSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.STRIPE;
    }

    @Override
    public String start(CheckoutPaymentContext ctx, RedirectAttributes ra) {

        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            ra.addFlashAttribute("error", "Stripe secret key mancante (stripe.secret-key / STRIPE_SECRET_KEY).");
            return "redirect:/checkout";
        }

        OrderTempData data = ctx.data();
        if (data == null) {
            ra.addFlashAttribute("error", "Dati checkout mancanti.");
            return "redirect:/checkout";
        }

        BigDecimal total = data.getTotalAmount() != null ? data.getTotalAmount() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            ra.addFlashAttribute("error", "Totale non valido per Stripe.");
            return "redirect:/checkout";
        }

        String normalizedSuccessUrl;
        String normalizedCancelUrl;

        try {
            normalizedSuccessUrl = normalizeAbsoluteUrl(successUrl, "/checkout/stripe/success?session_id={CHECKOUT_SESSION_ID}", "app.payment.success-url");
            normalizedCancelUrl = normalizeAbsoluteUrl(cancelUrl, "/checkout/stripe/cancel", "app.payment.cancel-url");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", "Configurazione Stripe non valida: " + e.getMessage());
            return "redirect:/checkout";
        }

        // salvo anche in sessione (UX/debug)
        HttpSession httpSession = ctx.session();
        httpSession.setAttribute(CheckoutSession.TEMP_DATA, data);

        try {
            Stripe.apiKey = stripeSecretKey.trim();

            long amountCents = eurToCents(total);

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(normalizedSuccessUrl)
                    .setCancelUrl(normalizedCancelUrl)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency("eur")
                                                    .setUnitAmount(amountCents)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Totale ordine")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    );

            if (data.getCustomerEmail() != null && !data.getCustomerEmail().isBlank()) {
                paramsBuilder.setCustomerEmail(data.getCustomerEmail().trim());
            }

            SessionCreateParams params = paramsBuilder.build();

            Session stripeSession = Session.create(params);

            // snapshot DB: il webhook userà questo per creare l’ordine “vero”
            snapshotService.save(stripeSession.getId(), data);

            return "redirect:" + stripeSession.getUrl();

        } catch (StripeException e) {
            ra.addFlashAttribute("error", "Errore Stripe: " + e.getMessage());
            return "redirect:/checkout";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Errore imprevisto Stripe: " + e.getMessage());
            return "redirect:/checkout";
        }
    }

    private long eurToCents(BigDecimal eur) {
        return eur
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private String normalizeAbsoluteUrl(String configuredValue, String fallbackPath, String propertyName) {
        String value = configuredValue == null ? "" : configuredValue.trim();

        if (value.isBlank()) {
            value = fallbackPath;
        }

        value = replaceBaseUrlPlaceholder(value);

        if (value.startsWith("//")) {
            value = "https:" + value;
        }

        if (isAbsoluteHttpUrl(value)) {
            return value;
        }

        if (value.startsWith("/")) {
            String base = normalizedAppBaseUrl(propertyName);
            return base + value;
        }

        throw new IllegalStateException(propertyName + " deve essere una URL assoluta https://... oppure un path che inizia con /. Valore attuale: " + value);
    }

    private String replaceBaseUrlPlaceholder(String value) {
        String base = safeTrim(appBaseUrl);
        return value
                .replace("${APP_BASE_URL}", base)
                .replace("${app.base-url}", base)
                .replace("${app.base.url}", base);
    }

    private String normalizedAppBaseUrl(String propertyName) {
        String base = replaceBaseUrlPlaceholder(safeTrim(appBaseUrl));

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (!isAbsoluteHttpUrl(base)) {
            throw new IllegalStateException(propertyName + " è relativo ma app.base-url / APP_BASE_URL non è assoluto. Imposta APP_BASE_URL=https://dominio-pubblico");
        }

        return base;
    }

    private boolean isAbsoluteHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
