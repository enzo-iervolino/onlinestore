package com.delta.store.web.payment.processor;
import com.delta.store.web.payment.*;
import com.delta.store.web.payment.service.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
@Service
public class GiftCardPaymentProcessor implements PaymentProcessor {
    private final CheckoutAttemptService attempts;
    public GiftCardPaymentProcessor(CheckoutAttemptService attempts) { this.attempts=attempts; }
    public PaymentMethod supports() { return PaymentMethod.GIFTCARD; }
    public String start(CheckoutPaymentContext ctx, RedirectAttributes ra) {
        var order=attempts.settle(ctx.data().getCheckoutAttemptId(),"GIFTCARD",ctx.data().getCheckoutAttemptId(),BigDecimal.ZERO,"EUR");
        CheckoutSession.clearIfUnchanged(ctx.session(),ctx.data());
        return "redirect:/checkout/success?orderId="+order.getId();
    }
}
