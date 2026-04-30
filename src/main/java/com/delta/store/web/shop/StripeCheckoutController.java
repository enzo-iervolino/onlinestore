package com.delta.store.web.shop;

import com.delta.store.order.model.Order;
import com.delta.store.order.service.OrderService;
import com.delta.store.web.payment.CheckoutSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/checkout/stripe")
public class StripeCheckoutController {

    private final OrderService orderService;

    public StripeCheckoutController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/cancel")
    public String cancel(RedirectAttributes ra) {
        ra.addFlashAttribute("error", "Pagamento Stripe annullato.");
        return "redirect:/checkout";
    }

    /**
     * ✅ Success page SOLO UI:
     * - non crea ordini
     * - cerca l’ordine creato dal webhook via stripeSessionId
     * - se ancora non c’è (webhook in ritardo), mostra pending e ricarica
     */
    @GetMapping("/success")
    public String success(@RequestParam(name = "session_id", required = false) String sessionId,
                          HttpSession session,
                          Model model,
                          RedirectAttributes ra) {

        if (sessionId == null || sessionId.isBlank()) {
            ra.addFlashAttribute("error", "Sessione Stripe mancante.");
            return "redirect:/checkout";
        }

        Optional<Order> opt = orderService.findByStripeSessionId(sessionId);

        // webhook non ha ancora scritto l’ordine (può succedere per pochi secondi)
        if (opt.isEmpty()) {
            model.addAttribute("sessionId", sessionId);
            return "checkout/stripe-pending";
        }

        // ordine pronto: pulisco dati checkout in sessione
        CheckoutSession.clearAll(session);

        return "redirect:/checkout/success?orderId=" + opt.get().getId();
    }
}
