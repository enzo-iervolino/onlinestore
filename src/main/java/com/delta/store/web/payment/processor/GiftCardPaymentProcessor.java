package com.delta.store.web.payment.processor;


import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.service.GiftCardService;
import com.delta.store.order.model.Order;
import com.delta.store.order.service.OrderNotificationService;
import com.delta.store.order.service.OrderService;
import com.delta.store.web.payment.CheckoutPaymentContext;
import com.delta.store.web.payment.PaymentMethod;
import com.delta.store.web.payment.PaymentProcessor;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.math.BigDecimal;

@Service
public class GiftCardPaymentProcessor implements PaymentProcessor {

    private final OrderService orderService;
    private final GiftCardService giftCardService;
    private final OrderNotificationService orderNotificationService;

    public GiftCardPaymentProcessor(OrderService orderService,
                                    GiftCardService giftCardService,
                                    OrderNotificationService orderNotificationService) {
        this.orderService = orderService;
        this.giftCardService = giftCardService;
        this.orderNotificationService = orderNotificationService;
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.GIFTCARD;
    }

    @Override
    @Transactional(rollbackOn = Exception.class)
    public String start(CheckoutPaymentContext ctx, RedirectAttributes ra) {

        HttpSession session = ctx.session();

        // dati giftcard da OrderTempData (pulito)
        String code = ctx.data().getGiftCardCode();
        BigDecimal amount = ctx.data().getGiftCardAmount();

        if (code == null || code.isBlank() || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            ra.addFlashAttribute("error", "Gift Card non valida o importo mancante.");
            return "redirect:/checkout";
        }

        GiftCard card = giftCardService.validateGiftCard(code);

        // 1) crea ordine
        Order order = orderService.createOrderPaidByGiftcard(ctx.data());

        // 2) consuma gift card (atomico)
        giftCardService.consumeGiftCard(card, amount, order.getId());

        // 3) invio conferma + pdf (afterCommit, non rompe la transazione)
        orderNotificationService.sendOrderConfirmationAfterCommit(order);

        // 4) pulizia sessione checkout (cart incluso)
        CheckoutSession.clearAll(session);

        return "redirect:/checkout/success?orderId=" + order.getId();
    }
}
