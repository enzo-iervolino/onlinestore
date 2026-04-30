package com.delta.store.user.controller;

 

import com.delta.store.user.model.User;
import com.delta.store.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Questo metodo viene eseguito PRIMA di ogni view.
     * Rende currentUser disponibile a TUTTI i template.
     */
    @ModelAttribute
    public void addCurrentUser(Authentication authentication, Model model) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String email = authentication.getName(); // username = email
        User user = userService.findByEmail(email);

        if (user != null) {
            model.addAttribute("currentUser", user);
        }
    }
}
