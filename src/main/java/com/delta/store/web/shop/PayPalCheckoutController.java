package com.delta.store.web.shop;
import com.delta.store.web.payment.*;
import com.delta.store.web.payment.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequestMapping("/paypal")
public class PayPalCheckoutController {
    private final PayPalApiService paypal;
    private final CheckoutRecoveryService recovery;
    private final CheckoutAttemptService attempts;
    public PayPalCheckoutController(CheckoutRecoveryService recovery,CheckoutAttemptService attempts,PayPalApiService paypal) { this.recovery=recovery;this.attempts=attempts;this.paypal=paypal; }
    @PostMapping("/webhook") @ResponseBody
    public org.springframework.http.ResponseEntity<String> webhook(@RequestBody java.util.Map<String,Object> event,@RequestHeader java.util.Map<String,String> headers) {
        try {
            java.util.Map<String,String> normalized=new java.util.HashMap<>();headers.forEach((k,v)->normalized.put(k.toLowerCase(java.util.Locale.ROOT),v));
            if(!paypal.verifyWebhook(normalized,event)) return org.springframework.http.ResponseEntity.badRequest().body("invalid signature");
            String type=String.valueOf(event.get("event_type"));
            if(!java.util.Set.of("CHECKOUT.ORDER.APPROVED","PAYMENT.CAPTURE.COMPLETED","PAYMENT.CAPTURE.PENDING").contains(type))return org.springframework.http.ResponseEntity.ok("ok");
            Object value=event.get("resource");String reference=null;
            if(value instanceof java.util.Map<?,?> resource) {
                if("CHECKOUT.ORDER.APPROVED".equals(type))reference=String.valueOf(resource.get("id"));
                else if(resource.get("supplementary_data") instanceof java.util.Map<?,?> supplementary && supplementary.get("related_ids") instanceof java.util.Map<?,?> ids && ids.get("order_id")!=null)reference=String.valueOf(ids.get("order_id"));
            }
            if(reference!=null && (attempts.byReference(reference).isPresent()||attempts.hasLegacySnapshot("PAYPAL",reference))) recovery.paypal(reference);
            return org.springframework.http.ResponseEntity.ok("ok");
        }catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).error("Webhook PayPal da riprovare",ex);return org.springframework.http.ResponseEntity.internalServerError().body("retry");}
    }
    @GetMapping("/cancel")
    public String cancel(RedirectAttributes ra) { ra.addFlashAttribute("info","Il tentativo resta verificabile. Controlla lo stato prima di iniziare un altro pagamento."); return "redirect:/checkout/in-attesa"; }
    @GetMapping("/success")
    public String success(@RequestParam(name="token",required=false) String reference,HttpSession session,RedirectAttributes ra) {
        if(reference==null || !reference.matches("[A-Za-z0-9_\\-]{8,255}")) return "redirect:/checkout";
        try {
            var order=recovery.paypal(reference);
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
