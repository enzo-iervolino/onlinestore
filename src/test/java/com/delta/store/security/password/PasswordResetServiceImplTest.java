package com.delta.store.security.password;

import com.delta.store.email.EmailService;
import com.delta.store.user.model.User;
import com.delta.store.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PasswordResetServiceImplTest {
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final EmailService mail = mock(EmailService.class);
    private final PasswordResetServiceImpl service = new PasswordResetServiceImpl(
            tokens, users, mock(PasswordEncoder.class), mail);

    private User registeredUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 6L);
        user.setEmail("deltastore653@gmail.com");
        when(users.findByEmailForPasswordReset(user.getEmail())).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(service, "appBaseUrl", "https://ilmionegozioonline.it");
        return user;
    }

    @Test
    void repeatedRequestsReuseRowAndRenewTokenAndExpiry() throws Exception {
        User user = registeredUser();
        PasswordResetToken existing = new PasswordResetToken();
        ReflectionTestUtils.setField(existing, "id", 12L);
        existing.setUser(user);
        existing.setToken("previous-token");
        existing.setExpiryDate(LocalDateTime.now().minusHours(1));
        when(tokens.findByUserId(6L)).thenReturn(Optional.of(existing));

        service.createAndSendToken("  DELTASTORE653@gmail.com ", null);
        String firstToken = existing.getToken();
        assertThat(firstToken).isNotEqualTo("previous-token");
        assertThat(existing.getExpiryDate()).isAfter(LocalDateTime.now().plusMinutes(29));

        service.createAndSendToken(user.getEmail(), null);
        assertThat(existing.getId()).isEqualTo(12L);
        assertThat(existing.getToken()).isNotEqualTo(firstToken);
        verify(tokens, times(2)).save(same(existing));
        verify(tokens, never()).deleteByUserId(anyLong());
        verify(mail).sendPasswordResetEmail(user.getEmail(),
                "https://ilmionegozioonline.it/password/reset?token=" + firstToken);
    }

    @Test
    void createsTokenWhenMissing() throws Exception {
        User user = registeredUser();
        when(tokens.findByUserId(6L)).thenReturn(Optional.empty());
        assertThat(service.createAndSendToken(user.getEmail(), null)).isTrue();
        verify(tokens).save(argThat(token -> token.getId() == null
                && token.getUser() == user && token.getToken() != null
                && token.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(29))));
    }

    @Test
    void unknownEmailDoesNotCreateTokenOrSendMail() throws Exception {
        when(users.findByEmailForPasswordReset("missing@example.com")).thenReturn(Optional.empty());
        assertThat(service.createAndSendToken("missing@example.com", null)).isFalse();
        verifyNoInteractions(tokens, mail);
    }
}
