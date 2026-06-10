package com.delta.store.security.oauth2;

import com.delta.store.user.model.AuthProvider;
import com.delta.store.user.model.User;
import com.delta.store.user.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String DEFAULT_ROLE = "ROLE_USER";
    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String DEFAULT_AVATAR = "avatar-default.png";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());

        User user = saveOrUpdateOAuth2User(registrationId, attributes);

        return new DefaultOAuth2User(
                buildAuthorities(user),
                buildApplicationAttributes(attributes, user),
                "email"
        );
    }

    /**
     * Metodo condiviso anche dal flusso OIDC di Google.
     *
     * Google, con scope openid/email/profile, viene gestito da Spring come OpenID
     * Connect e quindi passa da OidcUserService, non sempre da OAuth2UserService.
     * Centralizzando qui la creazione/aggiornamento dell'utente evitiamo duplicati
     * e garantiamo che il record venga salvato nel DB in entrambi i flussi.
     */
    @Transactional
    public User saveOrUpdateOAuth2User(String registrationId, Map<String, Object> attributes) {
        AuthProvider authProvider = resolveProvider(registrationId);

        String providerId = clean(asString(attributes.get("sub")));
        String email = normalizeEmail(asString(attributes.get("email")));
        String firstname = clean(asString(attributes.get("given_name")));
        String lastname = clean(asString(attributes.get("family_name")));
        String fullname = clean(asString(attributes.get("name")));
        String picture = clean(asString(attributes.get("picture")));

        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2AuthenticationException("Google non ha restituito l'identificativo utente.");
        }

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Google non ha restituito l'email dell'utente.");
        }

        return userRepository.findByAuthProviderAndProviderId(authProvider, providerId)
                .or(() -> userRepository.findByEmail(email))
                .map(existingUser -> updateGoogleUser(
                        existingUser,
                        authProvider,
                        providerId,
                        email,
                        firstname,
                        lastname,
                        fullname,
                        picture
                ))
                .orElseGet(() -> createGoogleUser(
                        authProvider,
                        providerId,
                        email,
                        firstname,
                        lastname,
                        fullname,
                        picture
                ));
    }

    public List<GrantedAuthority> buildAuthorities(User user) {
        return List.of(new SimpleGrantedAuthority(normalizeRole(user.getRole())));
    }

    public Map<String, Object> buildApplicationAttributes(Map<String, Object> providerAttributes, User user) {
        Map<String, Object> applicationAttributes = new HashMap<>(providerAttributes);
        applicationAttributes.put("id", user.getId());
        applicationAttributes.put("email", user.getEmail());
        applicationAttributes.put("name", buildFullname(
                user.getFirstname(),
                user.getLastname(),
                user.getEmail(),
                user.getFullname()
        ));
        applicationAttributes.put("picture", user.getAvatar());
        return applicationAttributes;
    }

    private User updateGoogleUser(
            User user,
            AuthProvider authProvider,
            String providerId,
            String email,
            String firstname,
            String lastname,
            String fullname,
            String picture
    ) {
        user.setAuthProvider(authProvider);
        user.setProviderId(providerId);
        user.setEmail(email);
        user.setFirstname(firstname != null ? firstname : safe(user.getFirstname()));
        user.setLastname(lastname != null ? lastname : safe(user.getLastname()));
        user.setFullname(buildFullname(user.getFirstname(), user.getLastname(), email, fullname));

        if (picture != null && !picture.isBlank()) {
            user.setAvatar(picture);
        } else if (user.getAvatar() == null || user.getAvatar().isBlank()) {
            user.setAvatar(DEFAULT_AVATAR);
        }

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole(DEFAULT_ROLE);
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        }

        if (user.getPhone() == null || user.getPhone().isBlank()) {
            user.setPhone(buildTechnicalPhone(authProvider, providerId));
        }

        user.setEnabled(true);

        return userRepository.save(user);
    }

    private User createGoogleUser(
            AuthProvider authProvider,
            String providerId,
            String email,
            String firstname,
            String lastname,
            String fullname,
            String picture
    ) {
        User user = new User();
        user.setEmail(email);
        user.setFirstname(firstname != null ? firstname : "");
        user.setLastname(lastname != null ? lastname : "");
        user.setFullname(buildFullname(firstname, lastname, email, fullname));
        user.setPhone(buildTechnicalPhone(authProvider, providerId));
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(true);
        user.setRole(DEFAULT_ROLE);
        user.setAuthProvider(authProvider);
        user.setProviderId(providerId);
        user.setAvatar(picture != null && !picture.isBlank() ? picture : DEFAULT_AVATAR);

        return userRepository.save(user);
    }

    private AuthProvider resolveProvider(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new OAuth2AuthenticationException("Provider OAuth2 mancante.");
        }

        if (GOOGLE_REGISTRATION_ID.equalsIgnoreCase(registrationId)) {
            return AuthProvider.GOOGLE;
        }

        throw new OAuth2AuthenticationException("Provider OAuth2 non supportato: " + registrationId);
    }

    private String buildTechnicalPhone(AuthProvider authProvider, String providerId) {
        String safeProviderId = providerId == null || providerId.isBlank()
                ? UUID.randomUUID().toString()
                : providerId.replaceAll("[^A-Za-z0-9]", "");

        if (safeProviderId.length() > 60) {
            safeProviderId = safeProviderId.substring(0, 60);
        }

        return authProvider.name() + "_" + safeProviderId;
    }

    private String buildFullname(String firstname, String lastname, String fallbackEmail, String providerFullname) {
        if (providerFullname != null && !providerFullname.isBlank()) {
            return providerFullname.trim();
        }

        String first = firstname == null ? "" : firstname.trim();
        String last = lastname == null ? "" : lastname.trim();
        String full = (first + " " + last).trim();

        if (!full.isBlank()) {
            return full;
        }

        if (fallbackEmail != null && fallbackEmail.contains("@")) {
            return fallbackEmail.substring(0, fallbackEmail.indexOf('@'));
        }

        return "Cliente";
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        return role == null || role.isBlank() ? DEFAULT_ROLE : role.trim();
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
