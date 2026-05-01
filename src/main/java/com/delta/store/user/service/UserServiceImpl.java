package com.delta.store.user.service;

import com.delta.store.user.model.Address;
import com.delta.store.user.model.User;
import com.delta.store.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // =========================================
    // FIND BY EMAIL
    // =========================================
    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return userRepository
                .findByEmail(email.trim().toLowerCase())
                .orElse(null);
    }

    // =========================================
    // FIND BY ID
    // =========================================
    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return userRepository.findById(id);
    }

    // =========================================
    // SAVE USER
    // =========================================
    @Override
    public User save(User user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            user.setEmail(user.getEmail().trim().toLowerCase());
        }

        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            user.setPhone(normalizePhone(user.getPhone()));
        }

        return userRepository.save(user);
    }

    // =========================================
    // EXISTS BY EMAIL
    // =========================================
    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    // =========================================
    // EXISTS BY PHONE
    // =========================================
    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        return userRepository.existsByPhone(normalizePhone(phone));
    }

    // =========================================
    // CAMBIO PASSWORD
    // =========================================
    @Override
    public void changePassword(String email, String newPassword) {

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new IllegalStateException("Utente non trovato"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // =========================================
    // UPDATE AVATAR
    // =========================================
    @Override
    public String updateAvatar(String email, MultipartFile avatar) {

        if (avatar == null || avatar.isEmpty()) {
            throw new IllegalArgumentException("File avatar non valido");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new IllegalStateException("Utente non trovato"));

        String originalFilename = avatar.getOriginalFilename();
        String filename = System.currentTimeMillis() + "_" + originalFilename;

        Path uploadPath = Paths.get("uploads", "avatars");

        try {
            Files.createDirectories(uploadPath);
            Files.copy(
                    avatar.getInputStream(),
                    uploadPath.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            throw new RuntimeException("Errore upload avatar", e);
        }

        user.setAvatar(filename);
        userRepository.save(user);

        return filename;
    }

    // =========================================
    // CHANGE PHONE
    // =========================================
    @Override
    public void changePhone(String email, String newPhone) {

        if (newPhone == null || newPhone.isBlank()) {
            throw new IllegalArgumentException("Numero non valido");
        }

        String normalizedPhone = normalizePhone(newPhone);

        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalStateException("Numero di telefono già utilizzato");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new IllegalStateException("Utente non trovato"));

        user.setPhone(normalizedPhone);
        userRepository.save(user);
    }

    // =========================================
    // CHANGE ADDRESS
    // =========================================
    @Override
    public void changeAddress(String email, Address address) {

        if (address == null) {
            throw new IllegalArgumentException("Indirizzo non valido");
        }

        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new IllegalStateException("Utente non trovato"));

        user.setAddress(address);
        userRepository.save(user);
    }

    // =========================================
    // ADMIN
    // =========================================
    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                (User u) -> normalize(sortableName(u)),
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        ).thenComparing(
                                u -> normalize(u.getEmail()),
                                Comparator.nullsLast(String::compareToIgnoreCase)
                        )
                )
                .toList();
    }

    @Override
    public void toggleEnabled(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Utente non trovato"));

        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Override
    public void changeRole(Long userId, String role) {

        if (!role.equals("ROLE_USER") && !role.equals("ROLE_ADMIN")) {
            throw new IllegalArgumentException("Ruolo non valido");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Utente non trovato"));

        user.setRole(role);
        userRepository.save(user);
    }

    private String sortableName(User user) {
        if (user == null) {
            return "";
        }

        if (user.getFullname() != null && !user.getFullname().isBlank()) {
            return user.getFullname().trim();
        }

        String firstname = user.getFirstname() != null ? user.getFirstname().trim() : "";
        String lastname = user.getLastname() != null ? user.getLastname().trim() : "";
        String combined = (firstname + " " + lastname).trim();

        return combined.isBlank() ? "" : combined;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        return phone == null ? null : phone.trim().replaceAll("\\s+", "");
    }
}