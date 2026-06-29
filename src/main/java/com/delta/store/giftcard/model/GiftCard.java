package com.delta.store.giftcard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "gift_card")
public class GiftCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "initial_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialAmount;

    @Column(name = "remaining_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingAmount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "validity_months")
    private Integer validityMonths = 6;

    @Column(name = "recipient_first_name", length = 80)
    private String recipientFirstName;

    @Column(name = "recipient_last_name", length = 80)
    private String recipientLastName;

    @Column(name = "recipient_email", length = 160)
    private String recipientEmail;

    @Column(name = "personal_message", length = 1000)
    private String personalMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getInitialAmount() {
        return initialAmount;
    }

    public void setInitialAmount(BigDecimal initialAmount) {
        this.initialAmount = initialAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
