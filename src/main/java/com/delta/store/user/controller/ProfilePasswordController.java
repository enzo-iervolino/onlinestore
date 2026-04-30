package com.delta.store.user.controller;

import com.delta.store.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProfilePasswordController {

    private final UserService userService;

    public ProfilePasswordController(UserService userService) {
        this.userService = userService;
    }

    // ==================================================
    // MOSTRA FORM CAMBIO PASSWORD
    // ==================================================
    @GetMapping("/password/change-password")
    public String showChangePasswordForm(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        return "profile/change-password";
    }

    // ==================================================
    // POST GENERICO:
    // - se NON ci sono parametri → redirect al form
    // - se ci sono → cambia password
    // ==================================================
    @PostMapping("/password/change-password")
    public String handleChangePassword(
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            Authentication authentication,
            Model model
    ) {

        // 🔹 POST "vuoto" dalla profile-modal
        if (newPassword == null || confirmPassword == null) {
            return "redirect:/password/change-password";
        }

        // 🔹 POST reale dal form
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Le password non coincidono");
            return "profile/change-password";
        }

        String email = authentication.getName();
        userService.changePassword(email, newPassword);

        return "profile/password-success";
    }
}
