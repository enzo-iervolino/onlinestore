package com.delta.store.user.controller;

import com.delta.store.user.model.Address;
import com.delta.store.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProfileAddressController {

    private final UserService userService;

    public ProfileAddressController(UserService userService) {
        this.userService = userService;
    }

    // ============================
    // MOSTRA FORM
    // ============================
    @GetMapping("/profile/address")
    public String showAddressForm(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        return "profile/change-address";
    }

    // ============================
    // POST VUOTO (da modale)
    // ============================
    @PostMapping("/profile/address")
    public String redirectToForm() {
        return "redirect:/profile/address";
    }

    // ============================
    // SALVA INDIRIZZO
    // ============================
    @PostMapping("/profile/address/save")
    public String saveAddress(
            @RequestParam String street,
            @RequestParam String city,
            @RequestParam String zipcode,
            @RequestParam String province,
            Authentication authentication,
            Model model
    ) {

        try {
            Address address = new Address();
            address.setStreet(street);
            address.setCity(city);
            address.setZipcode(zipcode);
            address.setProvince(province);

            userService.changeAddress(authentication.getName(), address);

        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            return "profile/change-address";
        }

        return "redirect:/";
    }
}

