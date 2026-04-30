package com.delta.store.web.payment;

 

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class CheckoutPaymentService {

    private final Map<PaymentMethod, PaymentProcessor> processors = new EnumMap<>(PaymentMethod.class);

    public CheckoutPaymentService(List<PaymentProcessor> list) {
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

        return p.start(ctx, ra);
    }
}
