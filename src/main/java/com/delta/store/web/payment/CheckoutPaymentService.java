package com.delta.store.web.payment;

 

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckoutPaymentService {

    @org.springframework.beans.factory.annotation.Value("${stripe.secret-key:${stripe.secret.key:}}") private String stripeKey;
    private final com.delta.store.web.payment.service.PayPalApiService paypal;
    private final com.delta.store.web.payment.service.CheckoutAttemptService attempts;
    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    public CheckoutPaymentService(List<PaymentProcessor> list, com.delta.store.web.payment.service.CheckoutAttemptService attempts, com.delta.store.web.payment.service.PayPalApiService paypal) {
        this.attempts=attempts; this.paypal=paypal;
        for (PaymentProcessor p : list) {
            processors.put(p.supports(), p);
        }
    }

    public String startPayment(PaymentMethod method, CheckoutPaymentContext ctx, RedirectAttributes ra) {

        PaymentProcessor p = processors.get(method);

        if (p == null) {
            ra.addFlashAttribute("error", "Metodo di pagamento non supportato: " + method);
            return "redirect:/checkout";
        }

        if (method==PaymentMethod.CASH) { ra.addFlashAttribute("error","Il pagamento in contanti non è disponibile online"); return "redirect:/checkout"; }
        if ((method==PaymentMethod.STRIPE && (stripeKey==null || stripeKey.isBlank())) || (method==PaymentMethod.PAYPAL && !paypal.isShopConfigured())) { ra.addFlashAttribute("error","Metodo di pagamento temporaneamente non disponibile"); return "redirect:/checkout"; }
        String owner=(String)ctx.session().getAttribute(CheckoutSession.OWNER_TOKEN);
        var attempt=attempts.begin(ctx.data(),owner);
        ctx.session().setAttribute(CheckoutSession.ATTEMPT,attempt.id);
        return p.start(new CheckoutPaymentContext(attempts.data(attempt),ctx.session(),ctx.authentication()),ra);
    }
}
