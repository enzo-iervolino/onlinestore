package com.delta.store.security.password;

import com.delta.store.user.model.User;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/password")
public class PasswordResetController {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // ==================================================
    // FORGOT PASSWORD → INVIO EMAIL + TOKEN
    // ==================================================
    @GetMapping("/forgot")
    public String showForgotPasswordForm() {
        return "password/forgot-password";
    }

    @PostMapping("/forgot")
    public String processForgotPassword(
            @RequestParam("email") String email,
            @RequestParam(value = "fromModal", defaultValue = "false") boolean fromModal,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        return sendRecoveryEmail(email, request, redirectAttributes,
                fromModal ? "redirect:/" : "redirect:/password/forgot");
    }

    // ==================================================
    // RESET PASSWORD FORM
    // ==================================================
    @GetMapping("/reset")
    public String showResetPasswordForm(
            @RequestParam(value = "token", defaultValue = "") String token,
            Model model
    ) {
        if (token.isBlank()) {
            return "password/reset-invalid";
        }

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

        PasswordResetToken resetToken =
                passwordResetService.validatePasswordResetToken(token);

        if (resetToken == null) {
            return "password/reset-invalid";
        }

        if (password.length() < 6 || !password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", password.length() < 6
                    ? "La password deve contenere almeno 6 caratteri."
                    : "Le password non coincidono.");
            return "password/reset-password";
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
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/";
        }

        return sendRecoveryEmail(authentication.getName(), request, redirectAttributes,
                "redirect:/password/forgot");
    }

    private String sendRecoveryEmail(String email, HttpServletRequest request,
                                     RedirectAttributes redirectAttributes, String redirect) {
        try {
            if (passwordResetService.createAndSendToken(email, request)) {
                redirectAttributes.addFlashAttribute("forgotSent", true);
                return redirect;
            }
            redirectAttributes.addFlashAttribute("forgotError", "Email non registrata");
        } catch (MessagingException | MailException exception) {
            logger.warn("Invio dell’email di recupero fallito", exception);
            redirectAttributes.addFlashAttribute("forgotError", "Invio dell’email non riuscito. Riprova.");
        }

        redirectAttributes.addFlashAttribute("forgotEmail", email.trim());
        return redirect;
    }
}
