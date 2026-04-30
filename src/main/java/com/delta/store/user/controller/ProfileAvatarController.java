package com.delta.store.user.controller;

import com.delta.store.user.service.UserService;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ProfileAvatarController {

    private final UserService userService;

    public ProfileAvatarController(UserService userService) {
        this.userService = userService;
    }

    // ============================
    // MOSTRA FORM CAMBIO AVATAR
    // ============================
    @GetMapping("/profile/avatar")
    public String showAvatarForm(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        return "profile/change-avatar";
    }

    // ============================
    // POST GENERICO (da modale)
    // ============================
    @PostMapping("/profile/avatar")
    public String redirectToForm() {
        return "redirect:/profile/avatar";
    }

 // ============================
    // UPLOAD AVATAR (AJAX)
    // ============================
    @PostMapping("/profile/avatar/upload")
    @ResponseBody
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("avatar") MultipartFile avatar,
            Authentication authentication
    ) {

        if (avatar.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "File non valido")
            );
        }

        String email = authentication.getName();

        // salva avatar e ritorna il nome file
        String newAvatar = userService.updateAvatar(email, avatar);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "avatar", newAvatar
                )
        );
    }
}
