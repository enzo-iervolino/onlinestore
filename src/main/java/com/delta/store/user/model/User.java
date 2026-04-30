package com.delta.store.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstname;
    private String lastname;
    private String fullname;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private String password;

    private boolean enabled = true;

    @Column(nullable = false)
    private String role;

    /**
     * Provider di autenticazione usato dall'utente.
     * LOCAL = registrazione/login classico con email e password.
     * GOOGLE/FACEBOOK = login tramite provider OAuth2.
     *
     * Nota tecnica: la colonna resta nullable lato DDL perché nel database possono
     * già esistere utenti creati prima dell'integrazione OAuth2. Il default viene
     * gestito lato Java per i nuovi record.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * Identificativo univoco restituito dal provider OAuth2.
     * Per Google corrisponde normalmente all'attributo "sub".
     */
    @Column(length = 120)
    private String providerId;

    /**
     * Avatar: per utenti locali salva il nome file caricato in /uploads/avatars.
     * Per utenti OAuth2 può salvare anche una URL esterna restituita da Google.
     */
    @Column(length = 500)
    private String avatar;

    /**
     * Address è EMBEDDABLE nel tuo progetto => va EMBEDDED (NON OneToOne)
     */
    @Embedded
    private Address address;

    @PrePersist
    public void prePersist() {
        if (authProvider == null) {
            authProvider = AuthProvider.LOCAL;
        }
        if (role == null || role.isBlank()) {
            role = "ROLE_USER";
        }
        enabled = true;
    }

    // =======================
    // GETTER / SETTER
    // =======================

    public Long getId() {
        return id;
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

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
