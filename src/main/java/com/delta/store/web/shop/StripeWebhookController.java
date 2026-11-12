package com.delta.store.web.shop;



import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.service.GiftCardService;
import com.delta.store.giftcard.service.GiftCardNotificationService;
import com.delta.store.giftcard.service.GiftCardPaymentResult;
import com.delta.store.giftcard.service.GiftCardPurchaseService;
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
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final String METADATA_TYPE = "type";
    private static final String METADATA_PURCHASE_ID = "giftCardPurchaseId";
    private static final String METADATA_VALUE_GIFT_CARD = "GIFT_CARD";

    private final com.delta.store.web.payment.service.CheckoutRecoveryService recovery;
    private final OrderService orderService;
    private final OrderNotificationService orderNotificationService;
    private final CheckoutSnapshotService snapshotService;
    private final GiftCardService giftCardService;
    private final GiftCardPurchaseService giftCardPurchaseService;
    private final GiftCardNotificationService giftCardNotificationService;

    @Value("${stripe.webhook.secret:${stripe.webhook-secret:}}")
    private String webhookSecret;

    @Value("${stripe.secret-key:${stripe.secret.key:}}")
    private String stripeSecretKey;

    public StripeWebhookController(OrderService orderService,
                                   OrderNotificationService orderNotificationService,
                                   CheckoutSnapshotService snapshotService,
                                   GiftCardService giftCardService,
                                   GiftCardPurchaseService giftCardPurchaseService,
                                   GiftCardNotificationService giftCardNotificationService, com.delta.store.web.payment.service.CheckoutRecoveryService recovery) {
        this.orderService = orderService; this.recovery=recovery;
        this.orderNotificationService = orderNotificationService;
        this.snapshotService = snapshotService;
        this.giftCardService = giftCardService;
        this.giftCardPurchaseService = giftCardPurchaseService;
        this.giftCardNotificationService = giftCardNotificationService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String sigHeader
    ) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Webhook Stripe: manca stripe.webhook.secret / stripe.webhook-secret.");
            return ResponseEntity.internalServerError().body("webhook secret missing");
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            return ResponseEntity.badRequest().body("missing signature");
        }

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            if (!java.util.Set.of("checkout.session.completed","checkout.session.async_payment_succeeded","checkout.session.expired").contains(event.getType())) {
                return ResponseEntity.ok("ok");
            }

            if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
                return ResponseEntity.internalServerError().body("stripe key missing");
            }
            Stripe.apiKey = stripeSecretKey;

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
                log.warn("Session {} non paid (status={}) => non creo ordine/gift card.", sessionId, paymentStatus);
                if (!isGiftCardSession(fullSession)) recovery.stripe(sessionId);
                return ResponseEntity.ok("ok");
            }

            if (isGiftCardSession(fullSession)) {
                Long purchaseId = resolveGiftCardPurchaseId(fullSession)
                        .orElseThrow(() -> new IllegalStateException("Sessione Stripe gift card senza purchaseId"));

                GiftCardPaymentResult result = giftCardPurchaseService.completeStripePayment(
                        purchaseId,
                        sessionId,
                        centsToEur(fullSession.getAmountTotal())
                );

                if (result.newlyIssued()) {
                    String emailWarning = giftCardNotificationService.sendEmailIfRecipientPresent(result.giftCard());
                    if (emailWarning != null) {
                        log.warn(emailWarning);
                    }
                }

                log.info("Gift card emessa da webhook Stripe: purchaseId={} giftCardId={} stripeSessionId={}",
                        result.purchase().getId(),
                        result.giftCard().getId(),
                        sessionId);
                return ResponseEntity.ok("ok");
            }

            recovery.stripe(sessionId);
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

    private boolean isGiftCardSession(Session session) {
        if (session == null || session.getMetadata() == null) {
            return false;
        }

        String type = session.getMetadata().get(METADATA_TYPE);
        return METADATA_VALUE_GIFT_CARD.equalsIgnoreCase(type);
    }

    private Optional<Long> resolveGiftCardPurchaseId(Session session) {
        if (session == null) {
            return Optional.empty();
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata != null) {
            String purchaseIdValue = metadata.get(METADATA_PURCHASE_ID);
            if (purchaseIdValue != null && !purchaseIdValue.isBlank()) {
                try {
                    return Optional.of(Long.parseLong(purchaseIdValue.trim()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }

        return giftCardPurchaseService.findByStripeSessionId(session.getId()).map(com.delta.store.giftcard.model.GiftCardPurchase::getId);
    }

    private BigDecimal centsToEur(Long cents) {
        if (cents == null) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
