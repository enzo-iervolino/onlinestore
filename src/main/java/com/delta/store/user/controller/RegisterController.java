package com.delta.store.user.controller;

import com.delta.store.user.model.Address;
import com.delta.store.user.model.User;
import com.delta.store.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
public class RegisterController {

    private static final String AVATAR_UPLOAD_DIR = "uploads/avatars";

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public RegisterController(UserService userService,
                              PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String doRegister(
            @ModelAttribute("form") RegisterForm form,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
            Model model) {

        String email = normalize(form.getEmail());

        if (!StringUtils.hasText(email)
                || !StringUtils.hasText(form.getPassword())
                || !StringUtils.hasText(form.getConfirmPassword())) {
            model.addAttribute("errorMessage", "Compila i campi obbligatori.");
            return "register";
        }

        if (!form.getPassword().equals(form.getConfirmPassword())) {
            model.addAttribute("errorMessage", "Le password non coincidono.");
            return "register";
        }

        if (userService.existsByEmail(email)) {
            model.addAttribute("errorMessage", "Email già registrata.");
            return "register";
        }

        // ---------- AVATAR ----------
        String avatarFilename = null;


        if (avatarFile != null && !avatarFile.isEmpty()) {
            try {
                Files.createDirectories(Paths.get(AVATAR_UPLOAD_DIR));

                String ext = StringUtils.getFilenameExtension(
                        avatarFile.getOriginalFilename());

                avatarFilename = "user_" + UUID.randomUUID()
                        + (ext != null ? "." + ext : ".png");

                Path target = Paths.get(AVATAR_UPLOAD_DIR, avatarFilename);
                Files.copy(avatarFile.getInputStream(), target);

            } catch (IOException e) {
                model.addAttribute("errorMessage", "Errore upload avatar.");
                return "register";
            }
        }

        // ---------- USER ----------
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setFirstname(form.getFirstname());
        user.setLastname(form.getLastname());
        user.setFullname(
                (form.getFirstname() + " " + form.getLastname()).trim());
        user.setPhone(form.getPhone());
        user.setEnabled(true);
        user.setRole("ROLE_USER");
        user.setAvatar(avatarFilename);

        user.setAddress(buildAddress(form));
        userService.save(user);

        return "redirect:/?registered";
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private Address buildAddress(RegisterForm f) {
        if (!StringUtils.hasText(f.getStreet())
                && !StringUtils.hasText(f.getCity())
                && !StringUtils.hasText(f.getZipcode())
                && !StringUtils.hasText(f.getProvince())) {
            return null;
        }

        Address a = new Address();
        a.setStreet(f.getStreet());
        a.setCity(f.getCity());
        a.setZipcode(f.getZipcode());
        a.setProvince(f.getProvince());
        return a;
    }

    // DTO
    public static class RegisterForm {
        private String email;
        private String phone;
        private String password;
        private String confirmPassword;
        private String firstname;
        private String lastname;
        private String street;
        private String zipcode;
        private String city;
        private String province;

        // getters & setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getFirstname() { return firstname; }
        public void setFirstname(String firstname) { this.firstname = firstname; }
        public String getLastname() { return lastname; }
        public void setLastname(String lastname) { this.lastname = lastname; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getZipcode() { return zipcode; }
        public void setZipcode(String zipcode) { this.zipcode = zipcode; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getProvince() { return province; }
        public void setProvince(String province) { this.province = province; }
    }
}
