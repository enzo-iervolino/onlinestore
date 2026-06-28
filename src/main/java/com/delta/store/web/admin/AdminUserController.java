package com.delta.store.web.admin;

import com.delta.store.order.model.Order;
import com.delta.store.order.service.OrderService;
import com.delta.store.user.model.User;
import com.delta.store.user.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminUserController {

    private final UserService userService;
    private final OrderService orderService;

    public AdminUserController(UserService userService, OrderService orderService) {
        this.userService = userService;
        this.orderService = orderService;
    }

    // ===============================
    // LISTA UTENTI
    // ===============================
    @GetMapping("/admin/users")
    public String listUsers(Model model) {

        List<User> users = userService.findAll(); // NON paginato

        Map<Long, List<Order>> userOrdersMap = new LinkedHashMap<>();
        for (User user : users) {
            if (user != null && user.getId() != null) {
                userOrdersMap.put(user.getId(), orderService.findClientOrdersByUserId(user.getId()));
            }
        }

        model.addAttribute("users", users);
        model.addAttribute("userOrdersMap", userOrdersMap);

        return "admin/users/user-list";
    }

    // ===============================
    // SCHEDA UTENTE
    // ===============================
    @GetMapping("/admin/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {

        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalStateException("Utente non trovato"));

        model.addAttribute("user", user);
        return "admin/users/user-detail";
    }

    // ===============================
    // ABILITA / DISABILITA UTENTE
    // ===============================
    @PostMapping("/admin/users/toggle")
    public String toggleUser(@RequestParam Long userId) {

        userService.toggleEnabled(userId);
        return "redirect:/admin/users";
    }

    // ===============================
    // CAMBIO RUOLO
    // ===============================
    @PostMapping("/admin/users/role")
    public String changeRole(
            @RequestParam Long userId,
            @RequestParam String role
    ) {

        userService.changeRole(userId, role);
        return "redirect:/admin/users";
    }
}