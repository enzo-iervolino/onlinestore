package com.delta.store.product.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "size_options",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_size_options_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_size_options_code", columnList = "code"),
                @Index(name = "idx_size_options_name", columnList = "name_it")
        }
)
public class SizeOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Esempio: TU, XS, M, 42
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    // Esempio: TAGLIA UNICA, XS, 42
    @Column(name = "name_it", nullable = false, length = 80)
    private String nameIt;

    public SizeOption() {}

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNameIt() {
        return nameIt;
    }

    public void setNameIt(String nameIt) {
        this.nameIt = nameIt;
    }
}