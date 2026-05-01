package com.delta.store.user.controller;

import com.delta.store.user.model.Address;
import com.delta.store.user.model.User;
import com.delta.store.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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

    @GetMapping("/register/check-email")
    @ResponseBody
    public AvailabilityResponse checkEmail(@RequestParam("email") String email) {
        String normalizedEmail = normalizeEmail(email);

        if (!StringUtils.hasText(normalizedEmail)) {
            return new AvailabilityResponse(false, "");
        }

        boolean exists = userService.existsByEmail(normalizedEmail);

        if (exists) {
            return new AvailabilityResponse(true, "Email già registrata.");
        }

        return new AvailabilityResponse(false, "Email disponibile.");
    }

    @GetMapping("/register/check-phone")
    @ResponseBody
    public AvailabilityResponse checkPhone(@RequestParam("phone") String phone) {
        String normalizedPhone = normalizePhone(phone);

        if (!StringUtils.hasText(normalizedPhone)) {
            return new AvailabilityResponse(false, "");
        }

        boolean exists = userService.existsByPhone(normalizedPhone);

        if (exists) {
            return new AvailabilityResponse(true, "Telefono già registrato.");
        }

        return new AvailabilityResponse(false, "Telefono disponibile.");
    }

    @PostMapping("/register")
    public String doRegister(
            @ModelAttribute("form") RegisterForm form,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
            Model model) {

        String email = normalizeEmail(form.getEmail());
        String phone = normalizePhone(form.getPhone());

        if (!StringUtils.hasText(email)
                || !StringUtils.hasText(phone)
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

        if (userService.existsByPhone(phone)) {
            model.addAttribute("errorMessage", "Telefono già registrato.");
            return "register";
        }

        String avatarFilename = null;

        if (avatarFile != null && !avatarFile.isEmpty()) {
            try {
                Files.createDirectories(Paths.get(AVATAR_UPLOAD_DIR));

                String ext = StringUtils.getFilenameExtension(avatarFile.getOriginalFilename());

                avatarFilename = "user_" + UUID.randomUUID()
                        + (ext != null ? "." + ext : ".png");

                Path target = Paths.get(AVATAR_UPLOAD_DIR, avatarFilename);
                Files.copy(avatarFile.getInputStream(), target);

            } catch (IOException e) {
                model.addAttribute("errorMessage", "Errore upload avatar.");
                return "register";
            }
        }

        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setFirstname(trimToNull(form.getFirstname()));
        user.setLastname(trimToNull(form.getLastname()));
        user.setFullname(buildFullname(form.getFirstname(), form.getLastname()));
        user.setEnabled(true);
        user.setRole("ROLE_USER");
        user.setAvatar(avatarFilename);
        user.setAddress(buildAddress(form));

        userService.save(user);

        return "redirect:/?registered";
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        return phone == null ? null : phone.trim().replaceAll("\\s+", "");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String buildFullname(String firstname, String lastname) {
        String first = firstname != null ? firstname.trim() : "";
        String last = lastname != null ? lastname.trim() : "";
        String fullname = (first + " " + last).trim();

        return fullname.isBlank() ? null : fullname;
    }

    private Address buildAddress(RegisterForm f) {
        if (!StringUtils.hasText(f.getStreet())
                && !StringUtils.hasText(f.getCity())
                && !StringUtils.hasText(f.getZipcode())
                && !StringUtils.hasText(f.getProvince())) {
            return null;
        }

        Address address = new Address();
        address.setStreet(trimToNull(f.getStreet()));
        address.setCity(trimToNull(f.getCity()));
        address.setZipcode(trimToNull(f.getZipcode()));
        address.setProvince(trimToNull(f.getProvince()));

        return address;
    }

    public static class AvailabilityResponse {

        private final boolean exists;
        private final String message;

        public AvailabilityResponse(boolean exists, String message) {
            this.exists = exists;
            this.message = message;
        }

        public boolean isExists() {
            return exists;
        }

        public String getMessage() {
            return message;
        }
    }

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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }

        public String getFirstname() {
            return firstname;
        }

        public void setFirstname(String firstname) {
            this.firstname = firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public void setLastname(String lastname) {
            this.lastname = lastname;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getZipcode() {
            return zipcode;
        }

        public void setZipcode(String zipcode) {
            this.zipcode = zipcode;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getProvince() {
            return province;
        }

        public void setProvince(String province) {
            this.province = province;
        }
    }
}