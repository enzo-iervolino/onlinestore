package com.delta.store.web.payment.processor;

import com.delta.store.order.model.OrderTempData;
import com.delta.store.web.payment.CheckoutPaymentContext;
import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.web.payment.PaymentMethod;
import com.delta.store.web.payment.PaymentProcessor;
import com.delta.store.web.payment.service.CheckoutSnapshotService;
import com.delta.store.web.payment.service.PayPalApiService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Service
public class PayPalPaymentProcessor implements PaymentProcessor {

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

        if (!payPalApiService.isConfigured()) {
            ra.addFlashAttribute("error", "PayPal non configurato: inserisci paypal.client.id e paypal.client.secret in application.properties");
            return "redirect:/checkout";
        }

        OrderTempData data = ctx.data();
        if (data == null) {
            ra.addFlashAttribute("error", "Dati checkout mancanti.");
            return "redirect:/checkout";
        }

        BigDecimal total = data.getTotalAmount() != null ? data.getTotalAmount() : BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            ra.addFlashAttribute("error", "Totale non valido per PayPal.");
            return "redirect:/checkout";
        }

        try {
            // 1) crea PayPal order
            PayPalApiService.CreateOrderResult result = payPalApiService.createOrder(total);

            // 2) salvo snapshot per creare ordine SOLO dopo pagamento riuscito
            data.setSessionId(result.orderId());
            data.setPaymentMethod("PAYPAL");

            ctx.session().setAttribute(CheckoutSession.TEMP_DATA, data);
            snapshotService.savePayPal(result.orderId(), data);

            // 3) redirect buyer su PayPal
            return "redirect:" + result.approveUrl();

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Errore PayPal: " + e.getMessage());
            return "redirect:/checkout";
        }
    }
}
