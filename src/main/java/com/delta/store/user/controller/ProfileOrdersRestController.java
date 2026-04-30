package com.delta.store.user.controller;

import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderItem;
import com.delta.store.order.service.OrderPdfService;
import com.delta.store.order.service.OrderService;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/profile")
public class ProfileOrdersRestController {

    private final OrderService orderService;
    private final OrderPdfService orderPdfService;
    private final EntityManager entityManager;

    public ProfileOrdersRestController(OrderService orderService,
                                       OrderPdfService orderPdfService,
                                       EntityManager entityManager) {
        this.orderService = orderService;
        this.orderPdfService = orderPdfService;
        this.entityManager = entityManager;
    }

    @GetMapping("/orders")
    public List<UserOrderRowDTO> myOrders(@RequestParam(required = false) Long userId,
                                          Authentication authentication) {

        Long currentUserId = resolveCurrentUserId(authentication);

        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato");
        }

        if (userId != null && !userId.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accesso non consentito");
        }

        return orderService.findClientOrdersByUserId(currentUserId)
                .stream()
                .map(UserOrderRowDTO::from)
                .toList();
    }

    @GetMapping("/orders/{orderId}")
    public UserOrderDetailDTO orderDetail(@PathVariable Long orderId,
                                          Authentication authentication) {

        Order order = loadOwnedOrderWithItems(orderId, authentication);
        return UserOrderDetailDTO.from(order);
    }

    @GetMapping(value = "/orders/{orderId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> orderPdf(@PathVariable Long orderId,
                                           Authentication authentication) {

        Order order = loadOwnedOrderWithItems(orderId, authentication);

        byte[] pdf = orderPdfService.generateOrderPdf(order);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=ordine-" + orderId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Order loadOwnedOrderWithItems(Long orderId, Authentication authentication) {
        Long currentUserId = resolveCurrentUserId(authentication);

        if (currentUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato");
        }

        Optional<Order> opt = orderService.findByIdWithItems(orderId);
        if (opt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ordine non trovato");
        }

        Order order = opt.get();

        if (order.getUser() == null || order.getUser().getId() == null || !order.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accesso non consentito");
        }

        return order;
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String usernameOrEmail = authentication.getName();
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return null;
        }

        try {
            return entityManager.createQuery(
                            "select u.id from User u where lower(u.email) = :email",
                            Long.class)
                    .setParameter("email", usernameOrEmail.trim().toLowerCase())
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    public record UserOrderRowDTO(
            Long id,
            String createdDate,
            String paymentMethod,
            BigDecimal totalAmount
    ) {
        public static UserOrderRowDTO from(Order o) {
            return new UserOrderRowDTO(
                    o.getId(),
                    o.getCreatedDate() != null ? o.getCreatedDate().toString() : null,
                    detectPaymentMethod(o),
                    o.getTotalAmount()
            );
        }
    }

    public record UserOrderDetailDTO(
            Long id,
            String createdAt,
            String createdDate,
            String status,
            String paymentMethod,
            String customerName,
            String customerEmail,
            String customerAddress,
            BigDecimal subtotal,
            BigDecimal discountValue,
            BigDecimal shippingCost,
            BigDecimal totalAmount,
            BigDecimal giftcardAmount,
            List<UserOrderItemDTO> items
    ) {
        public static UserOrderDetailDTO from(Order o) {

            List<UserOrderItemDTO> items = (o.getItems() == null ? List.<OrderItem>of() : o.getItems().stream().toList())
                    .stream()
                    .sorted(Comparator.comparing(i -> i.getId() == null ? Long.MAX_VALUE : i.getId()))
                    .map(UserOrderItemDTO::from)
                    .toList();

            return new UserOrderDetailDTO(
                    o.getId(),
                    o.getCreatedAt() != null ? o.getCreatedAt().toString() : null,
                    o.getCreatedDate() != null ? o.getCreatedDate().toString() : null,
                    o.getStatus(),
                    detectPaymentMethod(o),
                    o.getCustomerName(),
                    o.getCustomerEmail(),
                    o.getCustomerAddress(),
                    o.getSubtotal(),
                    o.getDiscountValue(),
                    o.getShippingCost(),
                    o.getTotalAmount(),
                    o.getGiftcardAmount(),
                    items
            );
        }
    }

    public record UserOrderItemDTO(
            Long productId,
            Long variantId,
            String productCode,
            String variantCode,
            String sku,
            String color,
            String size,
            String productName,
            Double unitPrice,
            Integer quantity,
            Double lineTotal
    ) {
        public static UserOrderItemDTO from(OrderItem it) {
            double unit = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
            int qty = it.getQuantity() != null ? it.getQuantity() : 0;
            double line = unit * qty;

            String productCode = normalize(it.getProductCode());
            String variantCode = normalize(it.getVariantCode());

            String sku = variantCode != null ? variantCode : productCode;

            return new UserOrderItemDTO(
                    it.getProductId(),
                    it.getVariantId(),
                    productCode,
                    variantCode,
                    sku,
                    it.getColor(),
                    it.getSize(),
                    it.getProductName(),
                    it.getUnitPrice(),
                    it.getQuantity(),
                    line
            );
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

    private static String detectPaymentMethod(Order o) {
        if (o == null) return "—";
        if (o.getStripeSessionId() != null && !o.getStripeSessionId().isBlank()) return "STRIPE";
        if (o.getGiftcardCode() != null && !o.getGiftcardCode().isBlank()) return "GIFTCARD";
        if (o.getSessionId() != null && !o.getSessionId().isBlank()) return "PAYPAL";
        return "—";
    }
}