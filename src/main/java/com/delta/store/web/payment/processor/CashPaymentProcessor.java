package com.delta.store.web.payment.processor;
 
 
import com.delta.store.web.payment.CheckoutPaymentContext;
import com.delta.store.web.payment.PaymentMethod;
import com.delta.store.web.payment.PaymentProcessor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Service
public class CashPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.CASH;
    }

    @Override
    public String start(CheckoutPaymentContext ctx, RedirectAttributes ra) {
        ra.addFlashAttribute("info", "Contanti: step 4 da implementare (ordine in attesa + conferma).");
        return "redirect:/checkout";
    }
}
