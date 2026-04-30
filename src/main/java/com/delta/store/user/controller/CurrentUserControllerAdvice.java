package com.delta.store.user.controller;

import com.delta.store.user.model.User;
import com.delta.store.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserControllerAdvice {

    private final UserService userService;

    public CurrentUserControllerAdvice(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String email = authentication.getName(); // email dal login
        return userService.findByEmail(email);   // UTENTE DAL DB
    }
}