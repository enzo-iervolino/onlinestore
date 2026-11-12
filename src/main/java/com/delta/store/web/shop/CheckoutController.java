package com.delta.store.web.shop;

import com.delta.store.cart.model.Cart;
import com.delta.store.cart.model.CartItem;
import com.delta.store.discount.service.DiscountService;
import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.service.GiftCardService;
import com.delta.store.order.model.CartItemDTO;
import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderTempData;
import com.delta.store.order.repository.OrderRepository;
import com.delta.store.shipping.service.ShippingService;
import com.delta.store.user.model.Address;
import com.delta.store.user.model.User;
import com.delta.store.user.service.UserService;
import com.delta.store.web.payment.CheckoutPaymentContext;
import com.delta.store.web.payment.CheckoutPaymentService;
import com.delta.store.web.payment.CheckoutSession;
import com.delta.store.web.payment.PaymentMethod;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/checkout")
public class CheckoutController {

    private static final String VIEW_CHECKOUT = "checkout/checkout";
    private static final String VIEW_CHECKOUT_SUCCESS = "checkout/checkout-success";
    private static final String VIEW_CHECKOUT_CANCEL = "checkout/checkout-cancel";

    private final com.delta.store.web.payment.service.CheckoutQuoteService quotes;
    private final com.delta.store.web.payment.service.CheckoutAttemptService attempts;
    private final ShippingService shippingService;
    private final DiscountService discountService;
    private final GiftCardService giftCardService;
    private final OrderRepository orderRepository;
    private final UserService userService;
    private final CheckoutPaymentService checkoutPaymentService;

    public CheckoutController(ShippingService shippingService,
                              DiscountService discountService,
                              GiftCardService giftCardService,
                              OrderRepository orderRepository,
                              UserService userService,
                              CheckoutPaymentService checkoutPaymentService,
                              com.delta.store.web.payment.service.CheckoutQuoteService quotes,
                              com.delta.store.web.payment.service.CheckoutAttemptService attempts) {

        this.quotes = quotes; this.attempts = attempts;
        this.shippingService = shippingService;
        this.discountService = discountService;
        this.giftCardService = giftCardService;
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.checkoutPaymentService = checkoutPaymentService;
    }

    @GetMapping
    public String showCheckout(Model model,
                               HttpSession session,
                               Authentication authentication) {
        synchronized(session) {

        Cart cart = (Cart) session.getAttribute(CheckoutSession.CART);
        try { cart = quotes.refresh(cart); session.setAttribute(CheckoutSession.CART, cart); }
        catch (IllegalArgumentException ex) { model.addAttribute("error", ex.getMessage()); }
        CheckoutTotals totals = calculateTotals(cart, session);
        model.addAttribute("quoteToken", quoteToken(cart, totals));

        model.addAttribute("cart", cart);
        model.addAttribute("shippingCost", totals.shipping);
        model.addAttribute("discountAmount", totals.discount);
        model.addAttribute("giftAmount", totals.gift);
        model.addAttribute("finalTotal", totals.finalTotal);

        model.addAttribute("discountPercent", session.getAttribute(CheckoutSession.DISCOUNT_PERCENT));
        model.addAttribute("giftCode", session.getAttribute(CheckoutSession.GIFT_CODE));
        model.addAttribute("couponApplied", totals.discount.compareTo(BigDecimal.ZERO) > 0);
        model.addAttribute("giftApplied", totals.gift.compareTo(BigDecimal.ZERO) > 0);
        model.addAttribute("paymentMethod", session.getAttribute(CheckoutSession.PAYMENT_METHOD));

        boolean shippingLocked = false;
        CheckoutShippingForm shippingForm = null;

        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {

            User user = userService.findByEmail(authentication.getName());

            if (user != null) {
                shippingLocked = true;

                shippingForm = new CheckoutShippingForm();
                shippingForm.setEmail(user.getEmail());
                shippingForm.setTelefono(user.getPhone());
                shippingForm.setFullName(user.getFullname());

                Address addr = user.getAddress();
                if (addr != null) {
                    shippingForm.setIndirizzo(addr.getStreet());
                    shippingForm.setCap(addr.getZipcode());
                    shippingForm.setCitta(addr.getCity());
                    shippingForm.setProvincia(addr.getProvince());
                }
            }
        }

        if (shippingForm == null) {
            shippingForm = (CheckoutShippingForm) session.getAttribute(CheckoutSession.SHIPPING);
        }

        model.addAttribute("shippingForm", shippingForm);
        model.addAttribute("shippingLocked", shippingLocked);

        return VIEW_CHECKOUT;
            }
    }

    @PostMapping("/salva-spedizione")
    public String saveShipping(@ModelAttribute CheckoutShippingForm form,
                               @RequestParam(name = "paymentMethod", required = false) String paymentMethod,
                               @RequestParam(name = "quoteToken", required = false) String quoteToken,
                               HttpSession session,
                               RedirectAttributes ra,
                               Authentication authentication) {

        Cart cart = (Cart) session.getAttribute(CheckoutSession.CART);

        if (cart == null || cart.isEmpty()) {
            ra.addFlashAttribute("error", "Carrello vuoto");
            return "redirect:/checkout";
        }

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {

            form.normalize();
            session.setAttribute(CheckoutSession.SHIPPING, form);
        }

        if (paymentMethod != null && !paymentMethod.isBlank() && !normalizeAndSavePaymentMethod(paymentMethod, session)) {
            ra.addFlashAttribute("error", "Metodo di pagamento non valido"); return "redirect:/checkout";
        }
        return pay(session, ra, authentication, quoteToken);
    }

    @PostMapping("/paga")
    public String pay(HttpSession session, RedirectAttributes ra, Authentication authentication,
                      @RequestParam(name="quoteToken",required=false) String quoteToken) {
        synchronized (session) {
            try {
                if (session.getAttribute(CheckoutSession.OWNER_TOKEN) == null) session.setAttribute(CheckoutSession.OWNER_TOKEN, java.util.UUID.randomUUID().toString());
                var active = attempts.active((String)session.getAttribute(CheckoutSession.OWNER_TOKEN));
                if (active.isPresent()) return "redirect:/checkout/in-attesa";
                Cart cart = (Cart)session.getAttribute(CheckoutSession.CART);
                if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Carrello vuoto");
                Cart fresh = quotes.refresh(cart);
                session.setAttribute(CheckoutSession.CART, fresh);
                CheckoutTotals totals = calculateTotals(fresh, session);
                if (quoteToken == null || !quoteToken.equals(quoteToken(fresh, totals))) throw new IllegalArgumentException("Il riepilogo è cambiato: controlla prezzi, sconti e importi e conferma nuovamente");
                PaymentMethod method = PaymentMethod.fromSessionValue((String)session.getAttribute(CheckoutSession.PAYMENT_METHOD));
                if (totals.finalTotal.signum()==0) method=PaymentMethod.GIFTCARD;
                session.setAttribute(CheckoutSession.PAYMENT_METHOD,method.name());
                OrderTempData data=buildOrderTempData(fresh,totals,session,authentication);
                data.setCartFingerprint(com.delta.store.web.payment.service.CheckoutQuoteService.fingerprint(fresh));
                com.delta.store.web.payment.service.CheckoutQuoteService.validate(data);
                return checkoutPaymentService.startPayment(method,new CheckoutPaymentContext(data,session,authentication),ra);
            } catch (IllegalArgumentException ex) {
                ra.addFlashAttribute("error",ex.getMessage()); return "redirect:/checkout";
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).error("Checkout non completato",ex);
                ra.addFlashAttribute("error","Operazione da verificare. Non ripetere il pagamento: consulta lo stato del tentativo.");
                return "redirect:/checkout/in-attesa";
            }
        }
    }

    private String quoteToken(Cart cart, CheckoutTotals t) {
        return com.delta.store.web.payment.service.CheckoutQuoteService.hash(
            com.delta.store.web.payment.service.CheckoutQuoteService.fingerprint(cart)+"|"+t.discount+"|"+t.shipping+"|"+t.gift+"|"+t.finalTotal);
    }

    @PostMapping("/applica-sconto")
    public String applyCoupon(@RequestParam String couponCode,
                              HttpSession session,
                              RedirectAttributes ra) {

        try {
            if (couponCode == null || couponCode.trim().isEmpty()) {
                ra.addFlashAttribute("couponError", "Inserisci un codice sconto.");
                ra.addFlashAttribute("openCouponModal", true);
                return "redirect:/checkout";
            }

            Cart cart = (Cart) session.getAttribute(CheckoutSession.CART);
            BigDecimal subtotal = cart == null ? BigDecimal.ZERO : cart.getTotal();

            var opt = discountService.validateCode(couponCode.trim());

            if (opt.isPresent()) {
                int pct = opt.get().getPercentage();
                BigDecimal discount = discountService.calculateDiscount(subtotal, pct);

                if (pct < 0 || pct > 100) throw new IllegalArgumentException("Sconto non valido");
                session.setAttribute(CheckoutSession.COUPON_CODE, couponCode.trim());
                session.setAttribute(CheckoutSession.DISCOUNT_AMOUNT, discount);
                session.setAttribute(CheckoutSession.DISCOUNT_PERCENT, BigDecimal.valueOf(pct));

                ra.addFlashAttribute("couponSuccess", "Codice sconto applicato.");
            } else {
                ra.addFlashAttribute("couponError", "Codice sconto non valido.");
                ra.addFlashAttribute("openCouponModal", true);
            }

            recalcGiftProposal(session);
            return "redirect:/checkout";

        } catch (Exception ex) {
            ra.addFlashAttribute("couponError", "Errore durante la verifica del codice sconto. Riprova.");
            ra.addFlashAttribute("openCouponModal", true);
            return "redirect:/checkout";
        }
    }

    @PostMapping("/rimuovi-sconto")
    public String removeCoupon(HttpSession session,
                               RedirectAttributes ra) {

        session.removeAttribute(CheckoutSession.COUPON_CODE);
        session.removeAttribute(CheckoutSession.DISCOUNT_AMOUNT);
        session.removeAttribute(CheckoutSession.DISCOUNT_PERCENT);
        recalcGiftProposal(session);

        ra.addFlashAttribute("couponSuccess", "Codice sconto rimosso.");
        return "redirect:/checkout";
    }

    @PostMapping("/applica-giftcard")
    public String applyGiftcard(@RequestParam String giftCardCode,
                                HttpSession session,
                                RedirectAttributes ra) {

        /*
         * Procedura richiesta:
         * 1) click inserimento gift card -> apre modale gift card;
         * 2) inserimento codice;
         * 3) controllo esistenza;
         * 4) se non esiste -> messaggio "Carta inesistente" nella stessa modale;
         * 5) se esiste -> salvataggio dati in sessione, calcolo proposta importo,
         *    abilitazione/apertura modale importo da usufruire.
         */

        try {
            if (giftCardCode == null || giftCardCode.trim().isEmpty()) {
                clearGiftCardSession(session);
                ra.addFlashAttribute("giftError", "Inserisci un codice gift card.");
                ra.addFlashAttribute("openGiftCardModal", true);
                return "redirect:/checkout";
            }

            String normalizedCode = giftCardCode.trim();

            GiftCard card = giftCardService.validateGiftCard(normalizedCode);

            session.setAttribute(CheckoutSession.GIFT_CODE, normalizedCode);
            session.setAttribute(CheckoutSession.GIFT_REMAINING, card.getRemainingAmount());

            /*
             * Non applichiamo ancora l'importo all'ordine.
             * Prima prepariamo la proposta, poi apriamo la modale importo.
             */
            session.removeAttribute(CheckoutSession.GIFT_AMOUNT);
            recalcGiftProposal(session);

            ra.addFlashAttribute("giftSuccess", "Gift card trovata. Scegli l'importo da utilizzare.");
            ra.addFlashAttribute("openGiftModal", true);

            return "redirect:/checkout";

        } catch (IllegalArgumentException ex) {
            clearGiftCardSession(session);

            ra.addFlashAttribute("giftError", "Carta inesistente.");
            ra.addFlashAttribute("openGiftCardModal", true);

            return "redirect:/checkout";

        } catch (Exception ex) {
            clearGiftCardSession(session);

            ra.addFlashAttribute("giftError", "Errore durante la verifica della gift card. Riprova.");
            ra.addFlashAttribute("openGiftCardModal", true);

            return "redirect:/checkout";
        }
    }

    @PostMapping("/conferma-giftcard")
    public String confirmGiftcard(@RequestParam BigDecimal amount,
                                  HttpSession session,
                                  RedirectAttributes ra) {

        try {
            BigDecimal remaining = (BigDecimal) session.getAttribute(CheckoutSession.GIFT_REMAINING);

            if (remaining == null) {
                ra.addFlashAttribute("giftError", "Prima inserisci una gift card valida.");
                ra.addFlashAttribute("openGiftCardModal", true);
                return "redirect:/checkout";
            }

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                ra.addFlashAttribute("giftError", "Inserisci un importo gift card valido.");
                ra.addFlashAttribute("openGiftModal", true);
                return "redirect:/checkout";
            }

            if (amount.compareTo(remaining) > 0) {
                amount = remaining;
            }

            BigDecimal proposal = (BigDecimal) session.getAttribute(CheckoutSession.GIFT_PROPOSAL);
            if (proposal != null && amount.compareTo(proposal) > 0) {
                amount = proposal;
            }

            amount = amount.setScale(2, java.math.RoundingMode.UNNECESSARY);
            session.setAttribute(CheckoutSession.GIFT_AMOUNT, amount);
            ra.addFlashAttribute("giftSuccess", "Gift card applicata all'ordine.");

            return "redirect:/checkout";

        } catch (Exception ex) {
            ra.addFlashAttribute("giftError", "Errore durante l'applicazione della gift card. Riprova.");
            ra.addFlashAttribute("openGiftModal", true);
            return "redirect:/checkout";
        }
    }

    @PostMapping("/rimuovi-giftcard")
    public String removeGiftcard(HttpSession session,
                                 RedirectAttributes ra) {

        clearGiftCardSession(session);

        ra.addFlashAttribute("giftSuccess", "Gift card rimossa.");
        return "redirect:/checkout";
    }

    @PostMapping("/seleziona-metodo-pagamento")
    @ResponseBody
    public String selectPaymentMethod(@RequestParam String paymentMethod,
                                      HttpSession session) {

        return normalizeAndSavePaymentMethod(paymentMethod, session) ? "OK" : "ERROR";
    }

    @GetMapping("/success")
    public String success(@RequestParam Long orderId, Model model, HttpSession session, Authentication authentication) {
        Long userId=null;
        if (authentication!=null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            User user=userService.findByEmail(authentication.getName()); if (user!=null) userId=user.getId();
        }
        if (!attempts.ownsOrder(orderId,(String)session.getAttribute(CheckoutSession.OWNER_TOKEN),userId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Ordine non disponibile");

        Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
        model.addAttribute("order", order);

        return VIEW_CHECKOUT_SUCCESS;
    }

    @GetMapping("/cancel")
    public String cancel() {
        return VIEW_CHECKOUT_CANCEL;
    }


    private boolean normalizeAndSavePaymentMethod(String paymentMethod, HttpSession session) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return false;
        }

        try {
            PaymentMethod method = PaymentMethod.fromSessionValue(paymentMethod);
            session.setAttribute(CheckoutSession.PAYMENT_METHOD, method.name());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private OrderTempData buildOrderTempData(Cart cart,
                                             CheckoutTotals totals,
                                             HttpSession session,
                                             Authentication authentication) {

        OrderTempData data = new OrderTempData();

        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())) {

            User user = userService.findByEmail(authentication.getName());

            if (user != null) {
                data.setUserId(user.getId());
                data.setCustomerEmail(user.getEmail());
                data.setCustomerPhone(user.getPhone());
                data.setCustomerName(user.getFullname());

                Address addr = user.getAddress();
                if (addr != null) {
                    data.setShippingAddress1(addr.getStreet());
                    data.setShippingZip(addr.getZipcode());
                    data.setShippingCity(addr.getCity());
                    data.setShippingProvince(addr.getProvince());
                    data.setShippingCountry("IT");
                }
            }

        } else {

            CheckoutShippingForm form = (CheckoutShippingForm) session.getAttribute(CheckoutSession.SHIPPING);

            if (form != null) {
                form.normalize();

                data.setCustomerEmail(form.getEmail());
                data.setCustomerPhone(form.getTelefono());
                data.setCustomerName(form.getFullName());

                data.setShippingAddress1(form.getIndirizzo());
                data.setShippingZip(form.getCap());
                data.setShippingCity(form.getCitta());
                data.setShippingProvince(form.getProvincia());
                data.setShippingCountry("IT");
            }
        }

        data.setSubtotal(totals.subtotal);
        data.setShippingCost(totals.shipping);

        data.setDiscountPercent((BigDecimal) session.getAttribute(CheckoutSession.DISCOUNT_PERCENT));
        data.setDiscountValue(totals.discount);

        BigDecimal netSubtotal = totals.subtotal.subtract(totals.discount);
        if (netSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            netSubtotal = BigDecimal.ZERO;
        }

        data.setNetSubtotal(netSubtotal);
        data.setTotalAmount(totals.finalTotal);

        data.setGiftCardCode(totals.gift.signum()>0?(String) session.getAttribute(CheckoutSession.GIFT_CODE):null);
        data.setGiftCardAmount((BigDecimal) session.getAttribute(CheckoutSession.GIFT_AMOUNT));

        data.setPaymentMethod((String) session.getAttribute(CheckoutSession.PAYMENT_METHOD));

        List<CartItemDTO> items = new ArrayList<>();
        for (CartItem it : cart.getItems()) {
            CartItemDTO dto = new CartItemDTO();

            dto.setProductId(it.getProductId());
            dto.setVariantId(it.getVariantId());
            dto.setProductName(it.getName());

            dto.setProductCode(it.getProductCode());
            dto.setVariantCode(it.getVariantCode());

            dto.setSku(it.getVariantCode() != null && !it.getVariantCode().isBlank()
                    ? it.getVariantCode()
                    : it.getProductCode());

            dto.setColor(it.getColor());
            dto.setSize(it.getSize());
            dto.setQuantity(it.getQuantity());
            dto.setUnitPrice(it.getPrice());

            items.add(dto);
        }

        data.setCartItems(items);

        return data;
    }

    private CheckoutTotals calculateTotals(Cart cart, HttpSession session) {

        BigDecimal subtotal = cart == null ? BigDecimal.ZERO : cart.getTotal();
        BigDecimal shipping = shippingService.calculateShipping(subtotal);

        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        String code=(String)session.getAttribute(CheckoutSession.COUPON_CODE);
        var coupon=code==null?java.util.Optional.<com.delta.store.discount.model.DiscountCode>empty():discountService.validateCode(code);
        if (coupon.isPresent() && coupon.get().getPercentage()!=null && coupon.get().getPercentage()>=0 && coupon.get().getPercentage()<=100) {
            discount=discountService.calculateDiscount(subtotal,coupon.get().getPercentage()).min(subtotal);
            session.setAttribute(CheckoutSession.DISCOUNT_PERCENT,BigDecimal.valueOf(coupon.get().getPercentage()));
        } else { session.removeAttribute(CheckoutSession.DISCOUNT_PERCENT); }
        session.setAttribute(CheckoutSession.DISCOUNT_AMOUNT,discount);
        BigDecimal beforeGift=subtotal.subtract(discount).add(shipping).max(BigDecimal.ZERO);
        BigDecimal gift=(BigDecimal)session.getAttribute(CheckoutSession.GIFT_AMOUNT);
        if (gift==null) gift=BigDecimal.ZERO;
        if (gift.signum()>0) {
            try {
                GiftCard card=giftCardService.validateGiftCard((String)session.getAttribute(CheckoutSession.GIFT_CODE));
                gift=gift.min(card.getRemainingAmount()).min(beforeGift).max(BigDecimal.ZERO);
            } catch (Exception ex) { gift=BigDecimal.ZERO; }
        }
        gift=gift.setScale(2,java.math.RoundingMode.HALF_UP);
        session.setAttribute(CheckoutSession.GIFT_AMOUNT,gift);
        BigDecimal total=beforeGift.subtract(gift).setScale(2,java.math.RoundingMode.HALF_UP);

        return new CheckoutTotals(subtotal, shipping, discount, gift, total);
    }

    private void recalcGiftProposal(HttpSession session) {

        Cart cart = (Cart) session.getAttribute(CheckoutSession.CART);
        CheckoutTotals totals = calculateTotals(cart, session);

        BigDecimal remaining = (BigDecimal) session.getAttribute(CheckoutSession.GIFT_REMAINING);
        if (remaining == null) {
            session.removeAttribute(CheckoutSession.GIFT_PROPOSAL);
            return;
        }

        BigDecimal proposal = totals.subtotal.subtract(totals.discount).add(totals.shipping).max(BigDecimal.ZERO).min(remaining);
        if (proposal.compareTo(BigDecimal.ZERO) < 0) {
            proposal = BigDecimal.ZERO;
        }

        session.setAttribute(CheckoutSession.GIFT_PROPOSAL, proposal);
    }

    private void clearGiftCardSession(HttpSession session) {
        session.removeAttribute(CheckoutSession.GIFT_CODE);
        session.removeAttribute(CheckoutSession.GIFT_REMAINING);
        session.removeAttribute(CheckoutSession.GIFT_AMOUNT);
        session.removeAttribute(CheckoutSession.GIFT_PROPOSAL);
    }

    private record CheckoutTotals(BigDecimal subtotal,
                                  BigDecimal shipping,
                                  BigDecimal discount,
                                  BigDecimal gift,
                                  BigDecimal finalTotal) {
    }
}
