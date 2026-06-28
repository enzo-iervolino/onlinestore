package com.delta.store.user.controller;

import com.delta.store.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProfilePhoneController {

    private final UserService userService;

    public ProfilePhoneController(UserService userService) {
        this.userService = userService;
    }

    // ============================
    // MOSTRA FORM
    // ============================
    @GetMapping("/profile/phone")
    public String showPhoneForm(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        return "profile/change-phone";
    }

    // ============================
    // POST VUOTO (da modale)
    // ============================
    @PostMapping("/profile/phone")
    public String redirectToForm() {
        return "redirect:/profile/phone";
    }

    // ============================
    // SALVA TELEFONO
    // ============================
    @PostMapping("/profile/phone/save")
    public String savePhone(
            @RequestParam String phone,
            Authentication authentication,
            Model model
    ) {

        try {
            userService.changePhone(authentication.getName(), phone);
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "profile/change-phone";
        }

        return "redirect:/";
    }
}
