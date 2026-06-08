package com.delta.store.web.shop;



import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.service.GiftCardService;
import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderTempData;
import com.delta.store.order.service.OrderNotificationService;
import com.delta.store.order.service.OrderService;
import com.delta.store.web.payment.service.CheckoutSnapshotService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final OrderService orderService;
    private final OrderNotificationService orderNotificationService;
    private final CheckoutSnapshotService snapshotService;
    private final GiftCardService giftCardService;

    @Value("${stripe.webhook.secret:${stripe.webhook-secret:}}")
    private String webhookSecret;

    @Value("${stripe.secret-key:${stripe.secret.key:}}")
    private String stripeSecretKey;

    public StripeWebhookController(OrderService orderService,
                                   OrderNotificationService orderNotificationService,
                                   CheckoutSnapshotService snapshotService,
                                   GiftCardService giftCardService) {
        this.orderService = orderService;
        this.orderNotificationService = orderNotificationService;
        this.snapshotService = snapshotService;
        this.giftCardService = giftCardService;
    }

    @PostMapping("/webhook")
    @Transactional(rollbackOn = Exception.class)
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String sigHeader
    ) {

        if (cleanSecret(webhookSecret).isBlank()) {
            log.error("Webhook Stripe: manca stripe.webhook.secret / stripe.webhook-secret.");
            return ResponseEntity.internalServerError().body("webhook secret missing");
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            return ResponseEntity.badRequest().body("missing signature");
        }

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, cleanSecret(webhookSecret));

            if (!"checkout.session.completed".equals(event.getType())) {
                return ResponseEntity.ok("ok");
            }

            if (cleanSecret(stripeSecretKey).isBlank()) {
                return ResponseEntity.internalServerError().body("stripe key missing");
            }
            Stripe.apiKey = cleanSecret(stripeSecretKey);

            String sessionId = extractSessionFromEvent(event).map(Session::getId).orElse(null);
            if (sessionId == null || sessionId.isBlank()) {
                return ResponseEntity.internalServerError().body("missing session id");
            }

            // idempotenza
            Optional<Order> existing = orderService.findByStripeSessionId(sessionId);
            if (existing.isPresent()) {
                return ResponseEntity.ok("ok");
            }

            Session fullSession = Session.retrieve(sessionId);

            String paymentStatus = fullSession.getPaymentStatus();
            if (paymentStatus == null || !"paid".equalsIgnoreCase(paymentStatus)) {
                log.warn("Session {} non paid (status={}) => non creo ordine.", sessionId, paymentStatus);
                return ResponseEntity.ok("ok");
            }

            // ✅ recupero snapshot (dettagli completi).
            // In produzione non dobbiamo creare ordini vuoti se lo snapshot non esiste:
            // meglio far ritentare il webhook e mostrare pending dal success fallback.
            Optional<OrderTempData> snapshot = snapshotService.load(sessionId);
            if (snapshot.isEmpty()) {
                log.error("Webhook Stripe: pagamento paid ma snapshot checkout mancante. Non creo ordine vuoto. sessionId={}", sessionId);
                return ResponseEntity.internalServerError().body("checkout snapshot missing");
            }

            OrderTempData data = snapshot.get();

            // sicurezza: imposto sempre questi
            data.setSessionId(sessionId);
            data.setPaymentMethod("STRIPE");

            // fallback email/nome se mancanti nello snapshot
            if ((data.getCustomerEmail() == null || data.getCustomerEmail().isBlank())) {
                if (fullSession.getCustomerDetails() != null) {
                    data.setCustomerEmail(fullSession.getCustomerDetails().getEmail());
                    data.setCustomerName(data.getCustomerName() != null ? data.getCustomerName() : fullSession.getCustomerDetails().getName());
                    data.setCustomerPhone(data.getCustomerPhone() != null ? data.getCustomerPhone() : fullSession.getCustomerDetails().getPhone());
                } else {
                    data.setCustomerEmail(fullSession.getCustomerEmail());
                }
            }

            // totale incassato da Stripe
            if (fullSession.getAmountTotal() != null) {
                data.setTotalAmount(centsToEur(fullSession.getAmountTotal()));
            }

            // creo ordine “vero”
            Order order = orderService.createOrderPaidByStripe(data, sessionId);

            // consumo giftcard se usata parzialmente + Stripe
            if (data.getGiftCardCode() != null && !data.getGiftCardCode().isBlank()
                    && data.getGiftCardAmount() != null
                    && data.getGiftCardAmount().compareTo(BigDecimal.ZERO) > 0) {

                GiftCard card = giftCardService.validateGiftCard(data.getGiftCardCode());
                giftCardService.consumeGiftCard(card, data.getGiftCardAmount(), order.getId());
            }

            // email + pdf (afterCommit)
            orderNotificationService.sendOrderConfirmationAfterCommit(order);

            // pulisco snapshot
            snapshotService.delete(sessionId);

            log.info("Ordine creato da webhook: orderId={} stripeSessionId={}", order.getId(), sessionId);
            return ResponseEntity.ok("ok");

        } catch (SignatureVerificationException e) {
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (StripeException e) {
            log.error("Errore Stripe in webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("stripe error");
        } catch (Exception e) {
            log.error("Errore webhook Stripe: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("webhook error");
        }
    }

    private Optional<Session> extractSessionFromEvent(Event event) {
        try {
            var deser = event.getDataObjectDeserializer();
            Optional<StripeObject> obj = deser.getObject();
            if (obj.isPresent() && obj.get() instanceof Session s) {
                return Optional.of(s);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private BigDecimal centsToEur(Long cents) {
        if (cents == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String cleanSecret(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        if (cleaned.length() >= 2
                && ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'")))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        return cleaned.replace("\r", "").replace("\n", "").trim();
    }
}
