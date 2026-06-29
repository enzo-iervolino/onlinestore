package com.delta.store.giftcard.dto;

import java.math.BigDecimal;

public class GiftCardIssueForm {

    private String selectedAmount = "50.00";
    private BigDecimal customAmount;
    private Integer validityMonths = 6;
    private String recipientFirstName;
    private String recipientLastName;
    private String recipientEmail;
    private String personalMessage;
    private String paymentMethod = "STRIPE";

    public String getSelectedAmount() {
        return selectedAmount;
    }

    public void setSelectedAmount(String selectedAmount) {
        this.selectedAmount = selectedAmount;
    }

    public BigDecimal getCustomAmount() {
        return customAmount;
    }

    public void setCustomAmount(BigDecimal customAmount) {
        this.customAmount = customAmount;
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
