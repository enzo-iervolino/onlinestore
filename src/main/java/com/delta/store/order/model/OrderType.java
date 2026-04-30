package com.delta.store.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "type_order",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_type_order_options_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_type_order_options_code", columnList = "code"),
                @Index(name = "idx_type_order_options_name", columnList = "description")
        }
)
public class OrderType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 10)
    private String code;

    @Column(name = "description", nullable = false, length = 80)
    private String descri;

    public OrderType() {
    }

    public OrderType(Long id, String code, String descri) {
        this.id = id;
        this.code = code;
        this.descri = descri;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescri() {
        return descri;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setDescri(String descri) {
        this.descri = descri;
    }
}