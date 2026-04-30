package com.delta.store.web.shop;

import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.service.GiftCardService;
import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderTempData;
import com.delta.store.order.service.OrderNotificationService;
import com.delta.store.order.service.OrderService;
import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.web.payment.service.CheckoutSnapshotService;
import com.delta.store.web.payment.service.PayPalApiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
@RequestMapping("/paypal")
public class PayPalCheckoutController {

    private final PayPalApiService payPalApiService;
    private final CheckoutSnapshotService snapshotService;
    private final OrderService orderService;
    private final OrderNotificationService orderNotificationService;
    private final GiftCardService giftCardService;

    public PayPalCheckoutController(PayPalApiService payPalApiService,
                                    CheckoutSnapshotService snapshotService,
                                    OrderService orderService,
                                    OrderNotificationService orderNotificationService,
                                    GiftCardService giftCardService) {
        this.payPalApiService = payPalApiService;
        this.snapshotService = snapshotService;
        this.orderService = orderService;
        this.orderNotificationService = orderNotificationService;
        this.giftCardService = giftCardService;
    }

    @GetMapping("/cancel")
    public String cancel(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Pagamento PayPal annullato.");
        return "redirect:/checkout";
    }

    /**
     * PayPal ritorna di solito con ?token=ORDER_ID (&PayerID=...).
     * Qui facciamo la CAPTURE e, SOLO se COMPLETED, creiamo ordine + inviamo email.
     */
    @GetMapping("/success")
    @Transactional(rollbackFor = Exception.class)
    public String success(@RequestParam(name = "token", required = false) String orderId,
                          @RequestParam(name = "PayerID", required = false) String payerId,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {

        if (orderId == null || orderId.isBlank()) {
            ra.addFlashAttribute("error", "PayPal: token/orderId mancante.");
            return "redirect:/checkout";
        }

        // idempotenza: se già creato, torno direttamente alla success
        Optional<Order> existing = orderService.findBySessionId(orderId);
        if (existing.isPresent()) {
            CheckoutSession.clearAll(session);
            return "redirect:/checkout/success?orderId=" + existing.get().getId();
        }

        if (!payPalApiService.isConfigured()) {
            ra.addFlashAttribute("error", "PayPal non configurato (paypal.client.id / paypal.client.secret).");
            return "redirect:/checkout";
        }

        // 1) CAPTURE (pagamento)
        PayPalApiService.OrderInfo info;
        try {
            info = payPalApiService.captureOrder(orderId);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "PayPal capture KO: " + e.getMessage());
            return "redirect:/checkout";
        }

        String status = info != null ? info.status() : null;
        if (status == null || !"COMPLETED".equalsIgnoreCase(status)) {
            // non è stato pagato davvero => non creo ordine
            ra.addFlashAttribute("error", "PayPal non completato (status=" + status + ").");
            return "redirect:/checkout";
        }

        // 2) recupero snapshot checkout
        OrderTempData data = snapshotService.loadPayPal(orderId)
                .orElseGet(() -> (OrderTempData) session.getAttribute(CheckoutSession.TEMP_DATA));

        if (data == null) {
            ra.addFlashAttribute("error", "Dati checkout mancanti (snapshot scaduto o sessione persa). Riprova il checkout.");
            return "redirect:/checkout";
        }

        // sicurezza
        data.setSessionId(orderId);
        data.setPaymentMethod("PAYPAL");

        // fallback email (se PayPal la fornisce)
        if ((data.getCustomerEmail() == null || data.getCustomerEmail().isBlank())
                && info.payerEmail() != null && !info.payerEmail().isBlank()) {
            data.setCustomerEmail(info.payerEmail());
        }

        // imposto totale incassato (se presente) => allineo importo pagato
        if (info.capturedAmount() != null && info.capturedAmount().compareTo(BigDecimal.ZERO) > 0) {
            data.setTotalAmount(info.capturedAmount());
        }

        // 3) creo ordine "vero" (PAGATO)
        Order order = orderService.createOrderPaidByPayPal(data, orderId);

        // 4) consumo giftcard se usata parzialmente + PayPal
        if (data.getGiftCardCode() != null && !data.getGiftCardCode().isBlank()
                && data.getGiftCardAmount() != null
                && data.getGiftCardAmount().compareTo(BigDecimal.ZERO) > 0) {

            GiftCard card = giftCardService.validateGiftCard(data.getGiftCardCode());
            giftCardService.consumeGiftCard(card, data.getGiftCardAmount(), order.getId());
        }

        // 5) email + pdf (afterCommit)
        orderNotificationService.sendOrderConfirmationAfterCommit(order);

        // 6) pulizia snapshot + sessione checkout
        snapshotService.deletePayPal(orderId);
        CheckoutSession.clearAll(session);

        return "redirect:/checkout/success?orderId=" + order.getId();
    }
}
