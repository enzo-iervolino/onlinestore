package com.delta.store.order.controller;

import com.delta.store.order.model.Order;
import com.delta.store.order.repository.OrderRepository;
import com.delta.store.order.repository.OrderTypeRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepository;
    private final OrderTypeRepository orderTypeRepository;

    public AdminOrderController(OrderRepository orderRepository,
                                OrderTypeRepository orderTypeRepository) {
        this.orderRepository = orderRepository;
        this.orderTypeRepository = orderTypeRepository;
    }

    @GetMapping
    public String listOrders(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "dateFrom", required = false) LocalDate dateFrom,
            @RequestParam(name = "dateTo", required = false) LocalDate dateTo,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "typeOrderId", required = false) Long typeOrderId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            Model model,
            HttpServletRequest request
    ) {

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<Order> spec = (root, query, cb) -> {

            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("typeOrder", JoinType.LEFT);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (id != null) {
                predicates.add(cb.equal(root.get("id"), id));
            }

            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        dateFrom.atStartOfDay()
                ));
            }

            if (dateTo != null) {
                predicates.add(cb.lessThan(
                        root.get("createdAt"),
                        dateTo.plusDays(1).atStartOfDay()
                ));
            }

            if (StringUtils.hasText(email)) {
                predicates.add(cb.like(
                        cb.lower(root.get("customerEmail")),
                        "%" + email.trim().toLowerCase() + "%"
                ));
            }

            if (StringUtils.hasText(name)) {
                predicates.add(cb.like(
                        cb.lower(root.get("customerName")),
                        "%" + name.trim().toLowerCase() + "%"
                ));
            }

            if (typeOrderId != null) {
                predicates.add(cb.equal(
                        root.get("typeOrder").get("id"),
                        typeOrderId
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Order> ordersPage = orderRepository.findAll(spec, pageable);

        model.addAttribute("ordersPage", ordersPage);
        model.addAttribute("orders", ordersPage.getContent());

        model.addAttribute("filterId", id);
        model.addAttribute("filterDateFrom", dateFrom);
        model.addAttribute("filterDateTo", dateTo);
        model.addAttribute("filterEmail", email);
        model.addAttribute("filterName", name);
        model.addAttribute("typeOrderId", typeOrderId);

        model.addAttribute("orderTypes", orderTypeRepository.findAll());
        model.addAttribute("currentUri", request.getRequestURI());

        return "admin/orders/list";
    }

    @GetMapping("/{id}")
    public String viewOrder(
            @PathVariable Long id,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "userId", required = false) Long userId,
            Model model,
            RedirectAttributes ra
    ) {
        return orderRepository.findByIdWithItems(id)
                .map(order -> {
                    String backUrl = "/admin/orders";

                    if ("users".equalsIgnoreCase(from) && userId != null) {
                        backUrl = "/admin/users?openOrdersUserId=" + userId;
                    }

                    model.addAttribute("order", order);
                    model.addAttribute("backUrl", backUrl);
                    model.addAttribute("from", from);
                    model.addAttribute("userId", userId);
                    model.addAttribute("orderTypes", orderTypeRepository.findAll());

                    return "admin/orders/detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("errorMessage", "Ordine non trovato.");
                    return "redirect:/admin/orders";
                });
    }

    @PostMapping("/{id}/delete")
    public String deleteOrder(
            @PathVariable Long id,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "userId", required = false) Long userId,
            RedirectAttributes ra
    ) {

        final String redirectUrl = ("users".equalsIgnoreCase(from) && userId != null)
                ? "/admin/users?openOrdersUserId=" + userId
                : "/admin/orders";

        if (!orderRepository.existsById(id)) {
            ra.addFlashAttribute("errorMessage", "Ordine non trovato.");
            return "redirect:" + redirectUrl;
        }

        orderRepository.deleteById(id);
        ra.addFlashAttribute("successMessage", "Ordine eliminato correttamente.");
        return "redirect:" + redirectUrl;
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam("status") String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "userId", required = false) Long userId,
            RedirectAttributes ra
    ) {

        final String redirectUrl = ("users".equalsIgnoreCase(from) && userId != null)
                ? "/admin/orders/" + id + "?from=users&userId=" + userId
                : "/admin/orders/" + id;

        return orderRepository.findById(id)
                .map(order -> {
                    order.setStatus(status);
                    orderRepository.save(order);
                    ra.addFlashAttribute("successMessage", "Stato ordine aggiornato.");
                    return "redirect:" + redirectUrl;
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("errorMessage", "Ordine non trovato.");
                    return "redirect:/admin/orders";
                });
    }
}