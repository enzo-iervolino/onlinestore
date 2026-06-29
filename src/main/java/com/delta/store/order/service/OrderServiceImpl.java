package com.delta.store.order.service;

import com.delta.store.order.model.CartItemDTO;
import com.delta.store.order.model.Order;
import com.delta.store.order.model.OrderItem;
import com.delta.store.order.model.OrderTempData;
import com.delta.store.order.model.OrderType;
import com.delta.store.order.repository.OrderRepository;
import com.delta.store.order.repository.OrderTypeRepository;
import com.delta.store.product.service.StockService;
import com.delta.store.user.model.User;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderTypeRepository orderTypeRepository;
    private final EntityManager entityManager;
    private final StockService stockService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderTypeRepository orderTypeRepository,
                            EntityManager entityManager,
                            StockService stockService) {
        this.orderRepository = orderRepository;
        this.orderTypeRepository = orderTypeRepository;
        this.entityManager = entityManager;
        this.stockService = stockService;
    }

    @Override
    public Order OrderInsert(Order order) {
        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByStripeSessionId(String sessionId) {
        return orderRepository.findByStripeSessionId(sessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findBySessionId(String sessionId) {
        return orderRepository.findBySessionId(sessionId);
    }

    @Override
    public Order createOrderPaidByStripe(OrderTempData data, String stripeSessionId) {
        Order order = buildPaidOrder(data, "PAGATO CON STRIPE");
        order.setStripeSessionId(stripeSessionId);
        order.setSessionId(data.getSessionId() != null ? data.getSessionId() : stripeSessionId);

        Order savedOrder = orderRepository.save(order);
        stockService.decrementStockForPaidOrder(savedOrder);
        return savedOrder;
    }

    @Override
    public Order createOrderPaidByPayPal(OrderTempData data, String payPalOrderId) {
        Order order = buildPaidOrder(data, "PAGATO CON PAYPAL");
        order.setSessionId(payPalOrderId);

        Order savedOrder = orderRepository.save(order);
        stockService.decrementStockForPaidOrder(savedOrder);
        return savedOrder;
    }

    @Override
    public Order createOrderPaidByGiftcard(OrderTempData data) {
        Order order = buildPaidOrder(data, "PAGATO CON GIFTCARD");

        Order savedOrder = orderRepository.save(order);
        stockService.decrementStockForPaidOrder(savedOrder);
        return savedOrder;
    }

    private Order buildPaidOrder(OrderTempData data, String status) {
        Order order = new Order();
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());

        if (data.getUserId() != null) {
            User uRef = entityManager.getReference(User.class, data.getUserId());
            order.setUser(uRef);
        }

        order.setCustomerName(data.getCustomerName());
        order.setCustomerEmail(data.getCustomerEmail());
        order.setCustomerPhono(data.getCustomerPhone());

        order.setCustomerAddress(buildFullAddress(
                data.getShippingAddress1(),
                data.getShippingZip(),
                data.getShippingCity(),
                data.getShippingProvince()
        ));

        BigDecimal giftcardAmount = safeBD(data.getGiftCardAmount());
        order.setGiftcardCode(data.getGiftCardCode());
        order.setGiftcardAmount(giftcardAmount);

        applyOrderType(order, data);

        BigDecimal subtotal = BigDecimal.ZERO;

        if (data.getCartItems() != null) {
            for (CartItemDTO ci : data.getCartItems()) {

                BigDecimal unit = (ci.getUnitPrice() == null ? BigDecimal.ZERO : ci.getUnitPrice())
                        .setScale(2, RoundingMode.HALF_UP);

                int qty = Math.max(1, ci.getQuantity());
                BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));

                subtotal = subtotal.add(lineTotal);

                OrderItem oi = buildOrderItem(order, ci, unit, qty);
                order.addItem(oi);
            }
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        BigDecimal discountPct = safeBD(data.getDiscountPercent());
        BigDecimal discountValue = safeBD(data.getDiscountValue());
        BigDecimal netSubtotal = safeBD(data.getNetSubtotal());
        BigDecimal shipping = safeBD(data.getShippingCost());

        if (netSubtotal.compareTo(BigDecimal.ZERO) == 0 && subtotal.compareTo(BigDecimal.ZERO) > 0) {
            netSubtotal = subtotal
                    .subtract(discountValue)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalOrder = safeBD(data.getTotalAmount());
        if (totalOrder.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal totalBeforeGift = resolveTotalBeforeGift(subtotal, discountValue, shipping, netSubtotal);
            totalOrder = totalBeforeGift
                    .subtract(giftcardAmount)
                    .max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        order.setSubtotal(subtotal);
        order.setDiscountPct(discountPct);
        order.setDiscountValue(discountValue);
        order.setNetSubtotal(netSubtotal);
        order.setShippingCost(shipping);
        order.setTotalAmount(totalOrder);

        return order;
    }

    private BigDecimal resolveTotalBeforeGift(BigDecimal subtotal,
                                               BigDecimal discountValue,
                                               BigDecimal shipping,
                                               BigDecimal netSubtotal) {

        BigDecimal calculated = safeBD(subtotal)
                .subtract(safeBD(discountValue))
                .add(safeBD(shipping))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal saved = safeBD(netSubtotal)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return saved.compareTo(calculated) >= 0 ? saved : calculated;
    }

    private void applyOrderType(Order order, OrderTempData data) {
        String typeCode = data != null ? data.getType() : null;

        if (!StringUtils.hasText(typeCode)) {
            typeCode = "WEB";
        }

        Optional<OrderType> orderTypeOpt = orderTypeRepository.findByCodeIgnoreCase(typeCode.trim());
        orderTypeOpt.ifPresent(order::setTypeOrder);
    }

    private OrderItem buildOrderItem(Order order, CartItemDTO ci, BigDecimal unit, int qty) {
        OrderItem oi = new OrderItem();
        oi.setOrder(order);
        oi.setProductName(ci.getProductName() != null ? ci.getProductName() : "Articolo");
        oi.setUnitPrice(unit.doubleValue());
        oi.setQuantity(qty);

        oi.setProductId(ci.getProductId());
        oi.setVariantId(ci.getVariantId());
        oi.setProductCode(resolveProductCode(ci));
        oi.setVariantCode(resolveVariantCode(ci));
        oi.setColor(ci.getColor());
        oi.setSize(ci.getSize());

        return oi;
    }

    private String resolveProductCode(CartItemDTO ci) {
        if (StringUtils.hasText(ci.getProductCode())) {
            return ci.getProductCode().trim();
        }

        if (ci.getVariantId() == null && StringUtils.hasText(ci.getSku())) {
            return ci.getSku().trim();
        }

        return null;
    }

    private String resolveVariantCode(CartItemDTO ci) {
        if (StringUtils.hasText(ci.getVariantCode())) {
            return ci.getVariantCode().trim();
        }

        if (ci.getVariantId() != null && StringUtils.hasText(ci.getSku())) {
            return ci.getSku().trim();
        }

        return null;
    }

    private String buildFullAddress(String street, String zip, String city, String province) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(street)) {
            sb.append(street.trim());
        }

        String part2 = "";
        if (StringUtils.hasText(zip)) {
            part2 += zip.trim();
        }
        if (StringUtils.hasText(city)) {
            part2 += (part2.isEmpty() ? "" : " ") + city.trim();
        }
        if (StringUtils.hasText(province)) {
            part2 += " (" + province.trim() + ")";
        }

        if (!part2.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(part2);
        }

        return sb.toString();
    }

    private BigDecimal safeBD(BigDecimal v) {
        return v == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : v.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> SelAll() {
        return orderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> SelById(long id) {
        return orderRepository.findById(id);
    }

    @Override
    public void Delete(Order order) {
        orderRepository.delete(order);
    }

    @Override
    public void deleteById(Long id) {
        orderRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdWithItems(Long id) {
        return orderRepository.findByIdWithItems(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findClientOrdersByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findClientOrders(String email) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> search(LocalDate startDate, LocalDate endDate, String email, String status, String userType, Pageable pageable) {

        Specification<Order> spec = (root, query, cb) -> cb.conjunction();

        if (startDate != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
        }

        if (endDate != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThan(root.get("createdAt"), endDate.plusDays(1).atStartOfDay()));
        }

        if (StringUtils.hasText(email)) {
            String mail = email.trim().toLowerCase();
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("customerEmail")), "%" + mail + "%"));
        }

        if (StringUtils.hasText(status)) {
            String s = status.trim();
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("status"), s));
        }

        if (StringUtils.hasText(userType)) {
            String t = userType.trim().toLowerCase();
            spec = spec.and((root, query, cb) -> {
                if ("registered".equals(t) || "loggato".equals(t) || "user".equals(t)) {
                    return cb.isNotNull(root.get("user"));
                } else if ("guest".equals(t)) {
                    return cb.isNull(root.get("user"));
                }
                return cb.conjunction();
            });
        }

        return orderRepository.findAll(spec, pageable);
    }

    @Override
    public void updateStatus(Long id, String status) {
        orderRepository.findById(id).ifPresent(o -> {
            o.setStatus(status);
            orderRepository.save(o);
        });
    }
}