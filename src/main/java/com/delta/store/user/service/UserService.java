package com.delta.store.user.service;

import com.delta.store.user.model.Address;
import com.delta.store.user.model.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface UserService {

    /**
     * Trova l'utente per email.
     * Restituisce null se non esiste (compatibile con i controller).
     */
    User findByEmail(String email);

    /**
     * Trova l'utente per id.
     */
    Optional<User> findById(Long id);

    /**
     * Salva o aggiorna l'utente.
     */
    User save(User user);

    /**
     * Verifica se esiste già un utente con la stessa email.
     */
    boolean existsByEmail(String email);

    /**
     * Verifica se esiste già un utente con lo stesso numero di telefono.
     */
    boolean existsByPhone(String phone);

    // =========================
    // PROFILO UTENTE
    // =========================

    void changePassword(String email, String newPassword);

    /**
     * Aggiorna l'avatar e restituisce il nome file salvato.
     */
    String updateAvatar(String email, MultipartFile avatar);

    void changePhone(String email, String newPhone);

    void changeAddress(String email, Address address);

    // =========================
    // ADMIN
    // =========================

    List<User> findAll();

    void toggleEnabled(Long userId);

    void changeRole(Long userId, String role);
}