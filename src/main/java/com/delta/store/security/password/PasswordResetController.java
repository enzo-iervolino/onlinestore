package com.delta.store.security.password;

import com.delta.store.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // ==================================================
    // FORGOT PASSWORD → INVIO EMAIL + TOKEN
    // ==================================================
    @PostMapping("/forgot")
    public String processForgotPassword(
            @RequestParam("email") String email,
            HttpServletRequest request
    ) {

        // avvia la procedura (token + email)
        passwordResetService.createAndSendToken(email, request);

        // redirect neutro (sicurezza)
        return "redirect:/?forgotSent=true";
    }

    // ==================================================
    // RESET PASSWORD FORM
    // ==================================================
    @GetMapping("/reset")
    public String showResetPasswordForm(
            @RequestParam("token") String token,
            Model model
    ) {

        PasswordResetToken resetToken =
                passwordResetService.validatePasswordResetToken(token);

        if (resetToken == null) {
            return "password/reset-invalid";
        }

        model.addAttribute("token", token);
        return "password/reset-password";
    }

    // ==================================================
    // PROCESS RESET PASSWORD
    // ==================================================
    @PostMapping("/reset")
    public String processResetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model
    ) {

        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Le password non coincidono");
            return "password/reset-password";
        }

        PasswordResetToken resetToken =
                passwordResetService.validatePasswordResetToken(token);

        if (resetToken == null) {
            return "password/reset-invalid";
        }

        User user = resetToken.getUser();

        passwordResetService.updatePassword(user, password);
        passwordResetService.deleteToken(resetToken);

        return "password/reset-success";
    }

    // ==================================================
    // RICHIESTA DA PROFILO (UTENTE LOGGATO)
    // ==================================================
    @PostMapping("/request")
    public String requestPasswordResetFromProfile(
            Authentication authentication,
            HttpServletRequest request
    ) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/";
        }

        String email = authentication.getName();
        passwordResetService.createAndSendToken(email, request);

        return "redirect:/?forgotSent=true";
    }
}
