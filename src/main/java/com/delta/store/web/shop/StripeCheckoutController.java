package com.delta.store.web.shop;

import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.service.GiftCardService;
import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderTempData;
import com.delta.store.order.service.OrderNotificationService;
import com.delta.store.order.service.OrderService;
import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.web.payment.service.CheckoutSnapshotService;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Controller
@RequestMapping("/checkout/stripe")
public class StripeCheckoutController {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutController.class);

    private final OrderService orderService;
    private final CheckoutSnapshotService snapshotService;
    private final OrderNotificationService orderNotificationService;
    private final GiftCardService giftCardService;

    @Value("${stripe.secret-key:${stripe.secret.key:}}")
    private String stripeSecretKey;

    public StripeCheckoutController(OrderService orderService,
                                    CheckoutSnapshotService snapshotService,
                                    OrderNotificationService orderNotificationService,
                                    GiftCardService giftCardService) {
        this.orderService = orderService;
        this.snapshotService = snapshotService;
        this.orderNotificationService = orderNotificationService;
        this.giftCardService = giftCardService;
    }

    @GetMapping("/cancel")
    public String cancel(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Pagamento Stripe annullato.");
        return "redirect:/checkout";
    }

    /**
     * Success Stripe robusto:
     * - se il webhook ha già creato l'ordine, mostra la success ordine;
     * - se il webhook non è arrivato/non è configurato, verifica direttamente la sessione Stripe;
     * - se Stripe conferma payment_status=paid, crea ordine, consuma eventuale giftcard e invia email.
     */
    @GetMapping("/success")
    @Transactional(rollbackFor = Exception.class)
    public String success(@RequestParam(name = "session_id", required = false) String sessionId,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {

        if (sessionId == null || sessionId.isBlank()) {
            ra.addFlashAttribute("error", "Sessione Stripe mancante.");
            return "redirect:/checkout";
        }

        Optional<Order> existing = orderService.findByStripeSessionId(sessionId);
        if (existing.isPresent()) {
            CheckoutSession.clearAll(session);
            return "redirect:/checkout/success?orderId=" + existing.get().getId();
        }

        if (cleanSecret(stripeSecretKey).isBlank()) {
            log.error("Stripe success fallback impossibile: manca stripe.secret-key / STRIPE_SECRET_KEY.");
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("error", "Stripe non configurato correttamente: secret key mancante.");
            return "checkout/stripe-pending";
        }

        Session stripeSession;
        try {
            Stripe.apiKey = cleanSecret(stripeSecretKey);
            stripeSession = Session.retrieve(sessionId);
        } catch (Exception e) {
            log.error("Impossibile recuperare sessione Stripe {}", sessionId, e);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("error", "Impossibile verificare il pagamento Stripe: " + e.getMessage());
            return "checkout/stripe-pending";
        }

        String paymentStatus = stripeSession.getPaymentStatus();
        if (paymentStatus == null || !"paid".equalsIgnoreCase(paymentStatus)) {
            log.warn("Sessione Stripe {} non ancora pagata. payment_status={}", sessionId, paymentStatus);
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("paymentStatus", paymentStatus);
            return "checkout/stripe-pending";
        }

        OrderTempData data = snapshotService.load(sessionId)
                .orElseGet(() -> (OrderTempData) session.getAttribute(CheckoutSession.TEMP_DATA));

        if (data == null) {
            log.error("Pagamento Stripe pagato ma snapshot checkout mancante. sessionId={}", sessionId);
            ra.addFlashAttribute("error", "Pagamento Stripe completato, ma dati checkout non disponibili. Contatta l'assistenza con sessionId " + sessionId + ".");
            return "redirect:/checkout";
        }

        data.setSessionId(sessionId);
        data.setPaymentMethod("STRIPE");

        if (isBlank(data.getCustomerEmail())) {
            if (stripeSession.getCustomerDetails() != null) {
                data.setCustomerEmail(stripeSession.getCustomerDetails().getEmail());
                if (isBlank(data.getCustomerName())) {
                    data.setCustomerName(stripeSession.getCustomerDetails().getName());
                }
                if (isBlank(data.getCustomerPhone())) {
                    data.setCustomerPhone(stripeSession.getCustomerDetails().getPhone());
                }
            } else {
                data.setCustomerEmail(stripeSession.getCustomerEmail());
            }
        }

        if (stripeSession.getAmountTotal() != null) {
            data.setTotalAmount(centsToEur(stripeSession.getAmountTotal()));
        }

        Order order = orderService.createOrderPaidByStripe(data, sessionId);

        if (data.getGiftCardCode() != null && !data.getGiftCardCode().isBlank()
                && data.getGiftCardAmount() != null
                && data.getGiftCardAmount().compareTo(BigDecimal.ZERO) > 0) {

            GiftCard card = giftCardService.validateGiftCard(data.getGiftCardCode());
            giftCardService.consumeGiftCard(card, data.getGiftCardAmount(), order.getId());
        }

        orderNotificationService.sendOrderConfirmationAfterCommit(order);
        snapshotService.delete(sessionId);
        CheckoutSession.clearAll(session);

        log.info("Ordine creato da Stripe success fallback: orderId={} stripeSessionId={}", order.getId(), sessionId);
        return "redirect:/checkout/success?orderId=" + order.getId();
    }

    private BigDecimal centsToEur(Long cents) {
        if (cents == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
