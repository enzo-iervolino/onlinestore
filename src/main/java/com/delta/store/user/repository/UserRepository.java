package com.delta.store.user.repository;

import com.delta.store.user.model.AuthProvider;
import com.delta.store.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // Serializza le richieste di recupero, anche quando il token non esiste ancora.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForPasswordReset(@Param("email") String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

}
