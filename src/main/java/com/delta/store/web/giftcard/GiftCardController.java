package com.delta.store.web.giftcard;

import com.delta.store.giftcard.dto.GiftCardIssueForm;
import com.delta.store.giftcard.model.GiftCard;
import com.delta.store.giftcard.model.GiftCardPaymentMethod;
import com.delta.store.giftcard.model.GiftCardPurchase;
import com.delta.store.giftcard.service.GiftCardNotificationService;
import com.delta.store.giftcard.service.GiftCardPaymentResult;
import com.delta.store.giftcard.service.GiftCardPurchaseService;
import com.delta.store.user.model.User;
import com.delta.store.user.repository.UserRepository;
import com.delta.store.web.payment.service.PayPalApiService;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/giftcard")
public class GiftCardController {

    private static final Logger log = LoggerFactory.getLogger(GiftCardController.class);
    private static final String VIEW_EMISSIONE = "giftcard/emissione";
    private static final String METADATA_TYPE = "type";
    private static final String METADATA_PURCHASE_ID = "giftCardPurchaseId";
    private static final String METADATA_VALUE_GIFT_CARD = "GIFT_CARD";

    private final GiftCardPurchaseService giftCardPurchaseService;
    private final GiftCardNotificationService giftCardNotificationService;
    private final UserRepository userRepository;
    private final PayPalApiService payPalApiService;

    @Value("${stripe.secret-key:${stripe.secret.key:}}")
    private String stripeSecretKey;

    @Value("${app.base-url:http://localhost:8081}")
    private String appBaseUrl;

    public GiftCardController(GiftCardPurchaseService giftCardPurchaseService,
                              GiftCardNotificationService giftCardNotificationService,
                              UserRepository userRepository,
                              PayPalApiService payPalApiService) {
        this.giftCardPurchaseService = giftCardPurchaseService;
        this.giftCardNotificationService = giftCardNotificationService;
        this.userRepository = userRepository;
        this.payPalApiService = payPalApiService;
    }

    @GetMapping
    public String showEmissionPage(Model model) {
        prepareModel(model);
        return VIEW_EMISSIONE;
    }

    @PostMapping("/checkout")
    public String startGiftCardCheckout(@ModelAttribute("giftCardForm") GiftCardIssueForm form,
                                        Authentication authentication,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        Long pendingPurchaseId = null;

        try {
            User requester = resolveAuthenticatedUser(authentication);
            GiftCardPurchase purchase = giftCardPurchaseService.createPendingPurchase(form, requester);
            pendingPurchaseId = purchase.getId();

            if (purchase.getPaymentMethod() == GiftCardPaymentMethod.PAYPAL) {
                return startPayPalPayment(purchase, redirectAttributes);
            }

            return startStripePayment(purchase, requester, redirectAttributes);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            if (pendingPurchaseId != null) {
                giftCardPurchaseService.markFailed(pendingPurchaseId);
            }
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("giftCardForm", form);
            prepareModel(model);
            return VIEW_EMISSIONE;
        } catch (Exception ex) {
            if (pendingPurchaseId != null) {
                giftCardPurchaseService.markFailed(pendingPurchaseId);
            }
            log.error("Errore durante avvio pagamento gift card", ex);
            model.addAttribute("error", "Errore durante l'avvio del pagamento gift card: " + ex.getMessage());
            model.addAttribute("giftCardForm", form);
            prepareModel(model);
            return VIEW_EMISSIONE;
        }
    }

    @GetMapping("/stripe/success")
    public String stripeSuccess(@RequestParam(name = "session_id", required = false) String sessionId,
                                RedirectAttributes redirectAttributes) {
        if (sessionId == null || sessionId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Sessione Stripe gift card mancante.");
            return "redirect:/giftcard";
        }

        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Stripe non configurato correttamente: secret key mancante.");
            return "redirect:/giftcard";
        }

        try {
            Stripe.apiKey = stripeSecretKey.trim();
            Session stripeSession = Session.retrieve(sessionId);

            String paymentStatus = stripeSession.getPaymentStatus();
            if (paymentStatus == null || !"paid".equalsIgnoreCase(paymentStatus)) {
                redirectAttributes.addFlashAttribute("warning", "Pagamento Stripe non ancora confermato. Stato: " + paymentStatus + ".");
                return "redirect:/giftcard";
            }

            Long purchaseId = resolvePurchaseId(stripeSession)
                    .orElseThrow(() -> new IllegalStateException("Richiesta gift card non collegata alla sessione Stripe."));

            GiftCardPaymentResult result = giftCardPurchaseService.completeStripePayment(
                    purchaseId,
                    sessionId,
                    centsToEur(stripeSession.getAmountTotal())
            );

            addSuccessFlash(result, redirectAttributes);
            return "redirect:/giftcard";
        } catch (Exception ex) {
            log.error("Errore success Stripe gift card - sessionId={}", sessionId, ex);
            redirectAttributes.addFlashAttribute("error", "Pagamento Stripe ricevuto, ma emissione gift card non completata: " + ex.getMessage());
            return "redirect:/giftcard";
        }
    }

    @GetMapping("/paypal/success")
    public String paypalSuccess(@RequestParam(name = "token", required = false) String paypalOrderId,
                                RedirectAttributes redirectAttributes) {
        if (paypalOrderId == null || paypalOrderId.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "PayPal: token/orderId gift card mancante.");
            return "redirect:/giftcard";
        }

        if (!payPalApiService.isConfigured()) {
            redirectAttributes.addFlashAttribute("error", "PayPal non configurato: controlla le credenziali.");
            return "redirect:/giftcard";
        }

        try {
            PayPalApiService.OrderInfo infoBeforeCapture = payPalApiService.getOrder(paypalOrderId);
            String statusBeforeCapture = infoBeforeCapture != null ? infoBeforeCapture.status() : null;

            if (statusBeforeCapture != null
                    && !"APPROVED".equalsIgnoreCase(statusBeforeCapture)
                    && !"COMPLETED".equalsIgnoreCase(statusBeforeCapture)) {

                String approveUrl = infoBeforeCapture.approveUrl();
                if (approveUrl != null && !approveUrl.isBlank()) {
                    redirectAttributes.addFlashAttribute("error", "Completa prima l'approvazione del pagamento gift card su PayPal.");
                    return "redirect:" + approveUrl;
                }

                redirectAttributes.addFlashAttribute("error", "PayPal gift card non ancora approvato dal cliente. Stato: " + statusBeforeCapture + ".");
                return "redirect:/giftcard";
            }

            PayPalApiService.OrderInfo info = "COMPLETED".equalsIgnoreCase(statusBeforeCapture)
                    ? infoBeforeCapture
                    : payPalApiService.captureOrder(paypalOrderId);

            String status = info != null ? info.status() : null;
            if (status == null || !"COMPLETED".equalsIgnoreCase(status)) {
                redirectAttributes.addFlashAttribute("error", "PayPal gift card non completato. Stato: " + status + ".");
                return "redirect:/giftcard";
            }

            GiftCardPaymentResult result = giftCardPurchaseService.completePayPalPayment(
                    paypalOrderId,
                    info.capturedAmount()
            );

            addSuccessFlash(result, redirectAttributes);
            return "redirect:/giftcard";
        } catch (Exception ex) {
            log.error("Errore success PayPal gift card - paypalOrderId={}", paypalOrderId, ex);
            redirectAttributes.addFlashAttribute("error", "Pagamento PayPal ricevuto, ma emissione gift card non completata: " + ex.getMessage());
            return "redirect:/giftcard";
        }
    }

    @GetMapping("/cancel")
    public String cancel(@RequestParam(name = "purchaseId", required = false) Long purchaseId,
                         RedirectAttributes redirectAttributes) {
        giftCardPurchaseService.markCancelled(purchaseId);
        redirectAttributes.addFlashAttribute("error", "Pagamento gift card annullato. La gift card non è stata emessa.");
        return "redirect:/giftcard";
    }

    private String startStripePayment(GiftCardPurchase purchase,
                                      User requester,
                                      RedirectAttributes redirectAttributes) throws Exception {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key mancante.");
        }

        Stripe.apiKey = stripeSecretKey.trim();

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(absoluteUrl("/giftcard/stripe/success?session_id={CHECKOUT_SESSION_ID}"))
                .setCancelUrl(absoluteUrl("/giftcard/cancel?purchaseId=" + purchase.getId()))
                .putMetadata(METADATA_TYPE, METADATA_VALUE_GIFT_CARD)
                .putMetadata(METADATA_PURCHASE_ID, String.valueOf(purchase.getId()))
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount(eurToCents(purchase.getAmount()))
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Gift Card Online da € " + formatEuro(purchase.getAmount()))
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );

        if (requester.getEmail() != null && !requester.getEmail().isBlank()) {
            paramsBuilder.setCustomerEmail(requester.getEmail().trim());
        }

        Session stripeSession = Session.create(paramsBuilder.build());
        giftCardPurchaseService.attachStripeSession(purchase.getId(), stripeSession.getId());

        return "redirect:" + stripeSession.getUrl();
    }

    private String startPayPalPayment(GiftCardPurchase purchase,
                                      RedirectAttributes redirectAttributes) {
        if (!payPalApiService.isConfigured()) {
            throw new IllegalStateException("PayPal non configurato. Controlla PAYPAL_CLIENT_ID e PAYPAL_CLIENT_SECRET.");
        }

        PayPalApiService.CreateOrderResult result = payPalApiService.createOrder(
                purchase.getAmount(),
                absoluteUrl("/giftcard/paypal/success"),
                absoluteUrl("/giftcard/cancel?purchaseId=" + purchase.getId()),
                "Gift Card Online da € " + formatEuro(purchase.getAmount()),
                "GIFT_CARD_PURCHASE_" + purchase.getId()
        );

        giftCardPurchaseService.attachPayPalOrder(purchase.getId(), result.orderId());
        return "redirect:" + result.approveUrl();
    }

    private void addSuccessFlash(GiftCardPaymentResult result,
                                 RedirectAttributes redirectAttributes) {
        GiftCard giftCard = result.giftCard();

        redirectAttributes.addFlashAttribute("issuedGiftCardCode", giftCard.getCode());
        redirectAttributes.addFlashAttribute("issuedGiftCardAmount", formatEuro(giftCard.getInitialAmount()));
        redirectAttributes.addFlashAttribute("issuedGiftCardExpiration", giftCard.getExpirationDate());
        redirectAttributes.addFlashAttribute("issuedGiftCardRecipientEmail", giftCard.getRecipientEmail());

        if (result.newlyIssued()) {
            String emailWarning = giftCardNotificationService.sendEmailIfRecipientPresent(giftCard);
            if (emailWarning != null) {
                redirectAttributes.addFlashAttribute("warning", emailWarning);
            }
        } else {
            redirectAttributes.addFlashAttribute("warning", "La gift card era già stata emessa per questo pagamento. Non è stato creato un duplicato.");
        }
    }

    private Optional<Long> resolvePurchaseId(Session stripeSession) {
        if (stripeSession == null) {
            return Optional.empty();
        }

        Map<String, String> metadata = stripeSession.getMetadata();
        if (metadata != null) {
            String type = metadata.get(METADATA_TYPE);
            String purchaseIdValue = metadata.get(METADATA_PURCHASE_ID);

            if (METADATA_VALUE_GIFT_CARD.equalsIgnoreCase(type)
                    && purchaseIdValue != null
                    && !purchaseIdValue.isBlank()) {
                try {
                    return Optional.of(Long.parseLong(purchaseIdValue.trim()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }

        return giftCardPurchaseService.findByStripeSessionId(stripeSession.getId()).map(GiftCardPurchase::getId);
    }

    private void prepareModel(Model model) {
        if (!model.containsAttribute("giftCardForm")) {
            model.addAttribute("giftCardForm", new GiftCardIssueForm());
        }
    }

    private User resolveAuthenticatedUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new IllegalStateException("Per acquistare una gift card devi essere autenticato.");
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Utente autenticato non trovato."));
    }

    private BigDecimal centsToEur(Long cents) {
        if (cents == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private long eurToCents(BigDecimal eur) {
        return eur
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    private String formatEuro(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String absoluteUrl(String path) {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        if (base.isBlank()) {
            base = "http://localhost:8081";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }
}
