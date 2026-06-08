package com.delta.store.security.password;

import com.delta.store.email.EmailService;
import com.delta.store.user.model.User;
import com.delta.store.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8081}")
    private String appBaseUrl;

    public PasswordResetServiceImpl(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService
    ) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    // ==================================================
    // CREATE TOKEN + SEND EMAIL
    // ==================================================
    @Override
    @Transactional
    public void createAndSendToken(String email, HttpServletRequest request) {

        User user = userRepository.findByEmail(email).orElse(null);

        // risposta neutra
        if (user == null) {
            return;
        }

        // 🔒 INVALIDA TOKEN PRECEDENTI (ORA FUNZIONA)
        tokenRepository.deleteByUserId(user.getId());

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(30));

        tokenRepository.save(resetToken);

        String resetUrl = buildResetUrl(request, token);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), resetUrl);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }


    private String buildResetUrl(HttpServletRequest request, String token) {
        String baseUrl = appBaseUrl;

        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = request.getScheme() + "://" + request.getServerName();

            int port = request.getServerPort();
            boolean standardHttp = "http".equalsIgnoreCase(request.getScheme()) && port == 80;
            boolean standardHttps = "https".equalsIgnoreCase(request.getScheme()) && port == 443;

            if (!standardHttp && !standardHttps) {
                baseUrl += ":" + port;
            }
        }

        baseUrl = baseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + "/password/reset?token=" + token;
    }

    // ==================================================
    // VALIDAZIONE TOKEN
    // ==================================================
    @Override
    public PasswordResetToken validatePasswordResetToken(String token) {

        PasswordResetToken resetToken =
                tokenRepository.findByToken(token).orElse(null);

        if (resetToken == null) {
            return null;
        }

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            return null;
        }

        if (resetToken.getUser() == null) {
            tokenRepository.delete(resetToken);
            return null;
        }

        return resetToken;
    }

    // ==================================================
    // UPDATE PASSWORD
    // ==================================================
    @Override
    public void updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // ==================================================
    // DELETE TOKEN
    // ==================================================
    @Override
    public void deleteToken(PasswordResetToken token) {
        tokenRepository.delete(token);
    }
}
