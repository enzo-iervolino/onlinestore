package com.delta.store.web.shop;
import com.delta.store.web.payment.*;
import com.delta.store.web.payment.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequestMapping("/checkout/stripe")
public class StripeCheckoutController {
    private final CheckoutRecoveryService recovery;
    private final CheckoutAttemptService attempts;
    public StripeCheckoutController(CheckoutRecoveryService recovery,CheckoutAttemptService attempts) { this.recovery=recovery;this.attempts=attempts; }
    @GetMapping("/cancel")
    public String cancel(RedirectAttributes ra) { ra.addFlashAttribute("info","Il tentativo resta verificabile. Controlla lo stato prima di iniziare un altro pagamento."); return "redirect:/checkout/in-attesa"; }
    @GetMapping("/success")
    public String success(@RequestParam(name="session_id",required=false) String reference,HttpSession session,RedirectAttributes ra) {
        if(reference==null || !reference.matches("[A-Za-z0-9_\\-]{8,255}")) return "redirect:/checkout";
        try {
            var order=recovery.stripe(reference);
            if(order.isPresent()) {
                var a=attempts.byReference(reference);
                if(a.isPresent() && a.get().ownerToken.equals(session.getAttribute(CheckoutSession.OWNER_TOKEN))) CheckoutSession.clearIfUnchanged(session,attempts.data(a.get()));
                return "redirect:/checkout/success?orderId="+order.get().getId();
            }
        } catch(Exception ex) { org.slf4j.LoggerFactory.getLogger(getClass()).error("Pagamento da verificare",ex); }
        ra.addFlashAttribute("error","Pagamento in verifica. Non effettuare un nuovo pagamento; conserva il riferimento per l'assistenza.");
        return "redirect:/checkout/in-attesa";
    }
}
