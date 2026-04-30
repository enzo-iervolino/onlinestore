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
        AuthProvider authProvider = resolveProvider(registrationId);

        if (authProvider != AuthProvider.GOOGLE) {
            throw new OAuth2AuthenticationException("Provider OAuth2 non supportato: " + registrationId);
        }

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());

        String providerId = asString(attributes.get("sub"));
        String email = normalizeEmail(asString(attributes.get("email")));
        String firstname = asString(attributes.get("given_name"));
        String lastname = asString(attributes.get("family_name"));
        String fullname = asString(attributes.get("name"));
        String picture = asString(attributes.get("picture"));

        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2AuthenticationException("Google non ha restituito l'identificativo utente.");
        }

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Google non ha restituito l'email dell'utente.");
        }

        User user = userRepository.findByEmail(email)
                .map(existingUser -> updateExistingGoogleUser(
                        existingUser,
                        authProvider,
                        providerId,
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

        String role = user.getRole();
        if (role == null || role.isBlank()) {
            role = DEFAULT_ROLE;
        }

        attributes.put("email", user.getEmail());
        attributes.put("name", user.getFullname());

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));

        return new DefaultOAuth2User(authorities, attributes, "email");
    }

    private User updateExistingGoogleUser(
            User user,
            AuthProvider authProvider,
            String providerId,
            String firstname,
            String lastname,
            String fullname,
            String picture
    ) {
        user.setAuthProvider(authProvider);
        user.setProviderId(providerId);

        if (isNotBlank(firstname)) {
            user.setFirstname(firstname.trim());
        }
        if (isNotBlank(lastname)) {
            user.setLastname(lastname.trim());
        }
        if (isNotBlank(fullname)) {
            user.setFullname(fullname.trim());
        } else {
            user.setFullname(buildFullname(user.getFirstname(), user.getLastname(), user.getEmail()));
        }
        if (isNotBlank(picture)) {
            user.setAvatar(picture.trim());
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
        user.setFirstname(isNotBlank(firstname) ? firstname.trim() : "");
        user.setLastname(isNotBlank(lastname) ? lastname.trim() : "");
        user.setFullname(isNotBlank(fullname) ? fullname.trim() : buildFullname(firstname, lastname, email));
        user.setPhone(buildTechnicalPhone(authProvider, providerId));
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(true);
        user.setRole(DEFAULT_ROLE);
        user.setAuthProvider(authProvider);
        user.setProviderId(providerId);
        user.setAvatar(isNotBlank(picture) ? picture.trim() : "avatar-default.png");

        return userRepository.save(user);
    }

    private AuthProvider resolveProvider(String registrationId) {
        if (registrationId == null) {
            throw new OAuth2AuthenticationException("Provider OAuth2 mancante.");
        }

        if ("google".equalsIgnoreCase(registrationId)) {
            return AuthProvider.GOOGLE;
        }

        if ("facebook".equalsIgnoreCase(registrationId)) {
            return AuthProvider.FACEBOOK;
        }

        return AuthProvider.LOCAL;
    }

    private String buildTechnicalPhone(AuthProvider authProvider, String providerId) {
        String safeProviderId = providerId == null ? UUID.randomUUID().toString() : providerId.replaceAll("[^A-Za-z0-9]", "");
        if (safeProviderId.length() > 60) {
            safeProviderId = safeProviderId.substring(0, 60);
        }
        return authProvider.name() + "_" + safeProviderId;
    }

    private String buildFullname(String firstname, String lastname, String fallbackEmail) {
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

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
