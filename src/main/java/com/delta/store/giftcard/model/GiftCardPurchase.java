package com.delta.store.giftcard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gift_card_purchase")
public class GiftCardPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "validity_months", nullable = false)
    private Integer validityMonths = 6;

    @Column(name = "recipient_first_name", length = 80)
    private String recipientFirstName;

    @Column(name = "recipient_last_name", length = 80)
    private String recipientLastName;

    @Column(name = "recipient_email", length = 160)
    private String recipientEmail;

    @Column(name = "personal_message", length = 1000)
    private String personalMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private GiftCardPaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GiftCardPurchaseStatus status = GiftCardPurchaseStatus.PENDING;

    @Column(name = "stripe_session_id", unique = true, length = 160)
    private String stripeSessionId;

    @Column(name = "paypal_order_id", unique = true, length = 160)
    private String paypalOrderId;

    @Column(name = "provider_reference", length = 180)
    private String providerReference;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gift_card_id", unique = true)
    private GiftCard giftCard;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.expiresAt == null) {
            this.expiresAt = now.plusHours(2);
        }
        if (this.status == null) {
            this.status = GiftCardPurchaseStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getValidityMonths() {
        return validityMonths;
    }

    public void setValidityMonths(Integer validityMonths) {
        this.validityMonths = validityMonths;
    }

    public String getRecipientFirstName() {
        return recipientFirstName;
    }

    public void setRecipientFirstName(String recipientFirstName) {
        this.recipientFirstName = recipientFirstName;
    }

    public String getRecipientLastName() {
        return recipientLastName;
    }

    public void setRecipientLastName(String recipientLastName) {
        this.recipientLastName = recipientLastName;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getPersonalMessage() {
        return personalMessage;
    }

    public void setPersonalMessage(String personalMessage) {
        this.personalMessage = personalMessage;
    }

    public GiftCardPaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(GiftCardPaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public GiftCardPurchaseStatus getStatus() {
        return status;
    }

    public void setStatus(GiftCardPurchaseStatus status) {
        this.status = status;
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

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public GiftCard getGiftCard() {
        return giftCard;
    }

    public void setGiftCard(GiftCard giftCard) {
        this.giftCard = giftCard;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getVersion() {
        return version;
    }
}
