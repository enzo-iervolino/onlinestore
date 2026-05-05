package com.delta.store.web.payment.processor;

import com.delta.store.order.model.OrderTempData;
import com.delta.store.web.payment.CheckoutPaymentContext;
import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.web.payment.PaymentMethod;
import com.delta.store.web.payment.PaymentProcessor;
import com.delta.store.web.payment.service.CheckoutSnapshotService;
import com.delta.store.web.payment.service.PayPalApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PayPalPaymentProcessor implements PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentProcessor.class);

    private final PayPalApiService payPalApiService;
    private final CheckoutSnapshotService snapshotService;

    public PayPalPaymentProcessor(PayPalApiService payPalApiService,
                                  CheckoutSnapshotService snapshotService) {
        this.payPalApiService = payPalApiService;
        this.snapshotService = snapshotService;
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.PAYPAL;
    }

    @Override
    public String start(CheckoutPaymentContext ctx, RedirectAttributes ra) {
        log.debug("Avvio pagamento PayPal");

        if (!payPalApiService.isConfigured()) {
            log.warn("PayPal non configurato: client id o secret mancanti");
            ra.addFlashAttribute(
                    "error",
                    "PayPal non configurato. Controlla PAYPAL_CLIENT_ID e PAYPAL_CLIENT_SECRET nelle variabili ambiente."
            );
            return "redirect:/checkout";
        }

        if (ctx == null) {
            log.warn("CheckoutPaymentContext nullo durante avvio PayPal");
            ra.addFlashAttribute("error", "Sessione checkout non valida.");
            return "redirect:/checkout";
        }

        OrderTempData data = ctx.data();

        if (data == null) {
            log.warn("OrderTempData mancante durante avvio PayPal");
            ra.addFlashAttribute("error", "Dati checkout mancanti.");
            return "redirect:/checkout";
        }

        BigDecimal total = data.getTotalAmount() != null
                ? data.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Totale non valido per PayPal: {}", total);
            ra.addFlashAttribute("error", "Totale non valido per PayPal.");
            return "redirect:/checkout";
        }

        try {
            PayPalApiService.CreateOrderResult result = payPalApiService.createOrder(total);

            data.setSessionId(result.orderId());
            data.setPaymentMethod("PAYPAL");

            ctx.session().setAttribute(CheckoutSession.TEMP_DATA, data);
            snapshotService.savePayPal(result.orderId(), data);

            log.debug("Redirect cliente verso PayPal - orderId={}", result.orderId());

            return "redirect:" + result.approveUrl();
        } catch (Exception e) {
            log.error("Errore durante creazione ordine PayPal", e);
            ra.addFlashAttribute("error", "Errore PayPal: " + e.getMessage());
            return "redirect:/checkout";
        }
    }
}