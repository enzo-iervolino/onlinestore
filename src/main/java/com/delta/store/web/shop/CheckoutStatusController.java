package com.delta.store.web.shop;
import com.delta.store.web.payment.*;
import com.delta.store.web.payment.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequestMapping("/checkout")
public class CheckoutStatusController {
    private final CheckoutAttemptService attempts;
    private final CheckoutRecoveryService recovery;
    private final CheckoutPaymentService payments;
    public CheckoutStatusController(CheckoutAttemptService attempts,CheckoutRecoveryService recovery,CheckoutPaymentService payments){this.attempts=attempts;this.recovery=recovery;this.payments=payments;}
    private java.util.Optional<com.delta.store.web.payment.model.CheckoutAttempt> owned(HttpSession session) {
        String owner=(String)session.getAttribute(CheckoutSession.OWNER_TOKEN);
        if(owner==null)return java.util.Optional.empty();
        var active=attempts.active(owner);if(active.isPresent())return active;
        return attempts.find((String)session.getAttribute(CheckoutSession.ATTEMPT)).filter(a->owner.equals(a.ownerToken));
    }
    @GetMapping("/in-attesa")
    public String status(HttpSession session,Model model) {
        var a=owned(session);
        if(a.isPresent()) {
            if("COMPLETED".equals(a.get().state)) { CheckoutSession.clearIfUnchanged(session,attempts.data(a.get()));return "redirect:/checkout/success?orderId="+a.get().orderId; }
            model.addAttribute("attempt",a.get());model.addAttribute("quote",attempts.data(a.get()));
        }
        return "checkout/checkout-pending";
    }
    @PostMapping("/verifica-pagamento")
    public String verify(HttpSession session,RedirectAttributes ra) {
        try {
            var a=owned(session).orElseThrow();
            if(a.providerReference!=null) { if("STRIPE".equals(a.provider))recovery.stripe(a.providerReference);else if("PAYPAL".equals(a.provider))recovery.paypal(a.providerReference); }
        } catch(Exception ex){ra.addFlashAttribute("error","Verifica non conclusa. Il tentativo resta conservato: non ripagare.");}
        return "redirect:/checkout/in-attesa";
    }
    @PostMapping("/annulla-tentativo")
    public String cancel(HttpSession session,RedirectAttributes ra) {
        try { recovery.cancel(owned(session).orElseThrow()); } catch(Exception ex){ra.addFlashAttribute("error","Annullamento da verificare. Attendi il recupero automatico o contatta l'assistenza.");}
        return "redirect:/checkout/in-attesa";
    }
}
