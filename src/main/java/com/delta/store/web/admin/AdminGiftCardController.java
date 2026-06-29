package com.delta.store.web.admin;

import com.delta.store.giftcard.model.GiftCardPaymentMethod;
import com.delta.store.giftcard.model.GiftCardPurchaseStatus;
import com.delta.store.giftcard.service.GiftCardAdminService;
import com.delta.store.giftcard.service.GiftCardAdminService.AdminGiftCardRow;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/giftcards")
public class AdminGiftCardController {

    private final GiftCardAdminService giftCardAdminService;

    public AdminGiftCardController(GiftCardAdminService giftCardAdminService) {
        this.giftCardAdminService = giftCardAdminService;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", required = false) String query,
                       @RequestParam(name = "status", required = false, defaultValue = "ALL") String status,
                       @RequestParam(name = "paymentMethod", required = false, defaultValue = "ALL") String paymentMethod,
                       @RequestParam(name = "purchaseStatus", required = false, defaultValue = "ALL") String purchaseStatus,
                       @RequestParam(name = "from", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(name = "to", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(name = "minAmount", required = false) BigDecimal minAmount,
                       @RequestParam(name = "maxAmount", required = false) BigDecimal maxAmount,
                       Model model) {

        List<AdminGiftCardRow> cards = giftCardAdminService.searchCards(
                query,
                status,
                paymentMethod,
                purchaseStatus,
                from,
                to,
                minAmount,
                maxAmount
        );

        model.addAttribute("cards", cards);
        model.addAttribute("summary", giftCardAdminService.buildSummary(cards));
        model.addAttribute("q", query);
        model.addAttribute("status", normalizeFilter(status));
        model.addAttribute("paymentMethod", normalizeFilter(paymentMethod));
        model.addAttribute("purchaseStatus", normalizeFilter(purchaseStatus));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);
        model.addAttribute("paymentMethods", GiftCardPaymentMethod.values());
        model.addAttribute("purchaseStatuses", GiftCardPurchaseStatus.values());
        model.addAttribute("hasActiveFilters", hasActiveFilters(query, status, paymentMethod, purchaseStatus, from, to, minAmount, maxAmount));

        return "admin/giftcards/list";
    }

    private boolean hasActiveFilters(String query,
                                     String status,
                                     String paymentMethod,
                                     String purchaseStatus,
                                     LocalDate from,
                                     LocalDate to,
                                     BigDecimal minAmount,
                                     BigDecimal maxAmount) {
        return hasText(query)
                || isActiveSelectFilter(status)
                || isActiveSelectFilter(paymentMethod)
                || isActiveSelectFilter(purchaseStatus)
                || from != null
                || to != null
                || minAmount != null
                || maxAmount != null;
    }

    private boolean isActiveSelectFilter(String value) {
        return hasText(value) && !"ALL".equalsIgnoreCase(value.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String normalizeFilter(String value) {
        return hasText(value) ? value.trim().toUpperCase() : "ALL";
    }
}
