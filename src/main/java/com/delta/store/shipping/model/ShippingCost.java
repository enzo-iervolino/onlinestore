package com.delta.store.shipping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipping_cost")
public class ShippingCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Il totale minimo è obbligatorio.")
    @DecimalMin(value = "0.00", message = "Il totale minimo non può essere negativo.")
    @Digits(integer = 10, fraction = 2, message = "Il totale minimo può avere al massimo 2 decimali.")
    @Column(name = "min_total", nullable = false)
    private BigDecimal minTotal;

    @DecimalMin(value = "0.00", message = "Il totale massimo non può essere negativo.")
    @Digits(integer = 10, fraction = 2, message = "Il totale massimo può avere al massimo 2 decimali.")
    @Column(name = "max_total")
    private BigDecimal maxTotal;

    @NotNull(message = "Il costo di spedizione è obbligatorio.")
    @DecimalMin(value = "0.00", message = "Il costo di spedizione non può essere negativo.")
    @Digits(integer = 10, fraction = 2, message = "Il costo di spedizione può avere al massimo 2 decimali.")
    @Column(nullable = false)
    private BigDecimal cost;

    @Column(nullable = false)
    private boolean active = true;

    @Size(max = 100, message = "La descrizione può contenere al massimo 100 caratteri.")
    @Column(length = 100)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getMinTotal() {
        return minTotal;
    }

    public void setMinTotal(BigDecimal minTotal) {
        this.minTotal = minTotal;
    }

    public BigDecimal getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(BigDecimal maxTotal) {
        this.maxTotal = maxTotal;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
