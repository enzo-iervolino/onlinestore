package com.delta.store.web.payment.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Durable web checkout and reservation. Does not change admin orders. */
@Entity
@Table(name = "web_checkout_attempts", indexes = @Index(name = "idx_web_attempt_state", columnList = "state,createdAt"))
public class CheckoutAttempt {
    @Id @Column(length = 36) public String id;
    @Column(nullable = false, length = 64) public String ownerToken;
    @Column(unique = true, length = 64) public String activeOwner;
    @Column(nullable = false, length = 20) public String provider;
    @Column(unique = true) public String providerReference;
    @Column(length = 2048) public String redirectUrl;
    @Column(nullable = false, length = 24) public String state;
    @Column(nullable = false, columnDefinition = "text") public String payload;
    public Long orderId;
    @Column(nullable = false) public LocalDateTime createdAt;
    @Column(nullable = false) public LocalDateTime expiresAt;
    @Column(nullable=false) public long gatewayExpiresAt;
    public LocalDateTime lastCheckedAt;
    public boolean reserved;
    @Column(length = 250) public String recoveryNote;
}
