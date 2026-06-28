package com.delta.store.security.password;

import com.delta.store.user.model.User;
import jakarta.servlet.http.HttpServletRequest;

public interface PasswordResetService {

    // invio email + creazione token
    void createAndSendToken(String email, HttpServletRequest request);

    // validazione token
    PasswordResetToken validatePasswordResetToken(String token);

    // update password
    void updatePassword(User user, String newPassword);

    // delete token
    void deleteToken(PasswordResetToken token);
}
