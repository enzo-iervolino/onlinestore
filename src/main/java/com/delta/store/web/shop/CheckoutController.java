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
                              CheckoutPaymentService checkoutPaymentService) {

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

        Cart cart = (Cart) session.getAttribute(CheckoutSession.CART);
        CheckoutTotals totals = calculateTotals(cart, session);

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

    @PostMapping("/salva-spedizione")
    public String saveShipping(@ModelAttribute CheckoutShippingForm form,
                               @RequestParam(name = "paymentMethod", required = false) String paymentMethod,
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

        normalizeAndSavePaymentMethod(paymentMethod, session);

        return "forward:/checkout/paga";
    }

    @PostMapping("/paga")
    public String pay(HttpSession session,
                      RedirectAttributes ra,
                      Authentication authentication) {

        Cart cart = (Cart) session.getAttribute(CheckoutSession.CART);

        if (cart == null || cart.isEmpty()) {
            ra.addFlashAttribute("error", "Carrello vuoto");
            return "redirect:/checkout";
        }

        CheckoutTotals totals = calculateTotals(cart, session);

        if (totals.finalTotal.compareTo(BigDecimal.ZERO) == 0) {
            session.setAttribute(CheckoutSession.PAYMENT_METHOD, "GIFTCARD");
        }

        String rawMethod = (String) session.getAttribute(CheckoutSession.PAYMENT_METHOD);
        PaymentMethod method = PaymentMethod.fromSessionValue(rawMethod);

        OrderTempData data = buildOrderTempData(cart, totals, session, authentication);

        CheckoutPaymentContext ctx = new CheckoutPaymentContext(data, session, authentication);

        return checkoutPaymentService.startPayment(method, ctx, ra);
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
    public String success(@RequestParam(name = "orderId", required = false) Long orderId,
                          Model model,
                          RedirectAttributes ra) {

        if (orderId == null) {
            ra.addFlashAttribute("error", "Ordine non confermato: identificativo ordine mancante.");
            return "redirect:/checkout";
        }

        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);
        if (order == null) {
            ra.addFlashAttribute("error", "Ordine non trovato. Se hai appena pagato, controlla la tua email o contatta l'assistenza.");
            return "redirect:/checkout";
        }

        if (order.getStatus() == null || !order.getStatus().toUpperCase().contains("PAGATO")) {
            ra.addFlashAttribute("error", "Ordine non ancora pagato/confermato. Riprova dal checkout.");
            return "redirect:/checkout";
        }

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

        netSubtotal = netSubtotal.add(totals.shipping);
        data.setNetSubtotal(netSubtotal);
        data.setTotalAmount(totals.finalTotal);

        data.setGiftCardCode((String) session.getAttribute(CheckoutSession.GIFT_CODE));
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

        BigDecimal discount = (BigDecimal) session.getAttribute(CheckoutSession.DISCOUNT_AMOUNT);
        if (discount == null) {
            discount = BigDecimal.ZERO;
        }

        BigDecimal gift = (BigDecimal) session.getAttribute(CheckoutSession.GIFT_AMOUNT);
        if (gift == null) {
            gift = BigDecimal.ZERO;
        }

        BigDecimal total = subtotal.subtract(discount).add(shipping).subtract(gift);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

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

        BigDecimal proposal = totals.finalTotal.min(remaining);
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
