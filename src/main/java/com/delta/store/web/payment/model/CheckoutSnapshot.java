package com.delta.store.web.payment.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "checkout_snapshots",
        indexes = {
                @Index(name = "idx_checkout_snapshots_stripe_session", columnList = "stripe_session_id", unique = true),
                @Index(name = "idx_checkout_snapshots_paypal_order", columnList = "paypal_order_id", unique = true),
                @Index(name = "idx_checkout_snapshots_expires_at", columnList = "expires_at")
        }
)
public class CheckoutSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stripe Checkout Session id (nullable, perché la stessa tabella gestisce anche PayPal) */
    @Column(name = "stripe_session_id", unique = true, nullable = true)
    private String stripeSessionId;

    /** PayPal Order id (nullable, perché la stessa tabella gestisce anche Stripe) */
    @Column(name = "paypal_order_id", unique = true, nullable = true)
    private String paypalOrderId;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (expiresAt == null) expiresAt = createdAt.plusHours(2);
    }

    public Long getId() {
        return id;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getPaypalOrderId() {
        return paypalOrderId;
    }

    public void setPaypalOrderId(String paypalOrderId) {
        this.paypalOrderId = paypalOrderId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
