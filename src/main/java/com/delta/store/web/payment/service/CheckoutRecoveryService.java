package com.delta.store.web.payment.service;

import com.delta.store.web.payment.model.CheckoutAttempt;
import com.delta.store.order.model.Order;
import com.delta.store.order.service.OrderService;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@EnableScheduling
public class CheckoutRecoveryService {
    private final CheckoutAttemptService attempts;
    private final PayPalApiService paypal;
    private final OrderService orders;
    @Value("${stripe.secret-key:${stripe.secret.key:}}") private String stripeKey;
    public CheckoutRecoveryService(CheckoutAttemptService attempts,PayPalApiService paypal,OrderService orders) { this.attempts=attempts;this.paypal=paypal;this.orders=orders; }
    @Scheduled(fixedDelayString="${checkout.recovery-delay-ms:60000}",initialDelay=30000)
    public void recover() {
        for (CheckoutAttempt a:attempts.pending()) {
            try {
                if ("GIFTCARD".equals(a.provider)) attempts.settle(a.id,"GIFTCARD",a.id,BigDecimal.ZERO,"EUR");
                else if (a.providerReference==null) {
                    // No redirect was exposed without binding; abandon only after the API request timeout window.
                    if (a.createdAt.plusMinutes(40).isBefore(LocalDateTime.now())) attempts.cancelVerifiedUnpaid(a.id);
                } else if ("STRIPE".equals(a.provider)) stripe(a.providerReference);
                else if ("PAYPAL".equals(a.provider)) paypal(a.providerReference);
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Recupero checkout {} non concluso; prenotazione conservata",a.id,ex);
            } finally {
                try { attempts.checked(a.id); } catch(Exception ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Stato controllo checkout non aggiornato",ex); }
            }
        }
    }
    public Optional<Order> stripe(String reference) throws com.stripe.exception.StripeException {
        var existing=orders.findByStripeSessionId(reference); if(existing.isPresent())return existing;
        if(stripeKey==null || stripeKey.isBlank()) throw new IllegalStateException("Stripe non disponibile");
        Stripe.apiKey=stripeKey.trim();
        var session=Session.retrieve(reference);
        if(session.getMetadata()!=null && "GIFT_CARD".equalsIgnoreCase(session.getMetadata().get("type"))) throw new IllegalArgumentException("Pagamento non relativo a un ordine shop");
        var a=attempts.byReference(reference);
        if("paid".equalsIgnoreCase(session.getPaymentStatus())) {
            BigDecimal paid=session.getAmountTotal()==null?null:BigDecimal.valueOf(session.getAmountTotal(),2);
            try { return Optional.of(a.isPresent()?attempts.settle(a.get().id,"STRIPE",reference,paid,session.getCurrency()):attempts.settleLegacy("STRIPE",reference,paid,session.getCurrency())); }
            catch(RuntimeException ex){ if(a.isPresent())attempts.markReview(a.get().id); throw ex; }
        }
        if(a.isPresent() && "expired".equals(session.getStatus())) attempts.cancelVerifiedUnpaid(a.get().id);
        return Optional.empty();
    }
    public Optional<Order> paypal(String reference) {
        var existing=orders.findBySessionId(reference); if(existing.isPresent())return existing;
        var a=attempts.byReference(reference);
        if(a.isEmpty() && !attempts.hasLegacySnapshot("PAYPAL",reference)) throw new IllegalArgumentException("Checkout PayPal non riconosciuto");
        var info=paypal.getOrder(reference);
        if("APPROVED".equalsIgnoreCase(info.status())) {
            var expected=attempts.expected("PAYPAL",reference);
            if(info.orderAmount()==null || !"EUR".equalsIgnoreCase(info.orderCurrency()) || expected.getTotalAmount().compareTo(info.orderAmount())!=0) throw new IllegalArgumentException("Importo/valuta PayPal non corrispondenti: nessuna capture effettuata");
            if(a.isPresent()) {
                if ("CANCELLED".equals(a.get().state)) return Optional.empty();
                if (a.get().expiresAt.isBefore(LocalDateTime.now()) && "PENDING".equals(a.get().state)) { attempts.cancelVerifiedUnpaid(a.get().id); return Optional.empty(); }
                attempts.capturing(a.get().id);
            }
            info=paypal.captureOrder(reference);
        }
        if("COMPLETED".equalsIgnoreCase(info.status())) {
            try { return Optional.of(a.isPresent()?attempts.settle(a.get().id,"PAYPAL",reference,info.capturedAmount(),info.capturedCurrency()):attempts.settleLegacy("PAYPAL",reference,info.capturedAmount(),info.capturedCurrency())); }
            catch(RuntimeException ex){ if(a.isPresent())attempts.markReview(a.get().id); throw ex; }
        }
        if(a.isPresent() && "PENDING".equals(a.get().state) && a.get().expiresAt.isBefore(LocalDateTime.now()) && ("CREATED".equalsIgnoreCase(info.status()) || "VOIDED".equalsIgnoreCase(info.status()))) attempts.cancelVerifiedUnpaid(a.get().id);
        return Optional.empty();
    }
    /** Cancel only when the provider proves the attempt cannot still become paid. */
    public void cancel(CheckoutAttempt a) throws com.stripe.exception.StripeException {
        if(a.providerReference==null) throw new IllegalStateException("Avvio in verifica: attendi il recupero automatico");
        if("STRIPE".equals(a.provider)) {
            Stripe.apiKey=stripeKey;
            var s=Session.retrieve(a.providerReference);
            if("open".equals(s.getStatus()) && !"paid".equals(s.getPaymentStatus())) s.expire();
            stripe(a.providerReference);
        } else {
            var info=paypal.getOrder(a.providerReference);
            if("CREATED".equalsIgnoreCase(info.status()) || "APPROVED".equalsIgnoreCase(info.status()) || "VOIDED".equalsIgnoreCase(info.status())) attempts.cancelVerifiedUnpaid(a.id);
            else paypal(a.providerReference);
        }
    }
}
