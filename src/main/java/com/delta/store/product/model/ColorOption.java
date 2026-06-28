package com.delta.store.product.model;

import jakarta.persistence.*;

@Entity
@Table(
        name = "color_options",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_color_options_code", columnNames = "code")
        },
        indexes = {
                @Index(name = "idx_color_options_code", columnList = "code"),
                @Index(name = "idx_color_options_name", columnList = "name_it")
        }
)
public class ColorOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Esempio: BLK, WHT, BEI
    @Column(name = "code", nullable = false, length = 10)
    private String code;

    // Esempio: NERO, BIANCO, BEIGE
    @Column(name = "name_it", nullable = false, length = 80)
    private String nameIt;

    // Esempio: #000000
    @Column(name = "hex", length = 7)
    private String hex;

    // Esempio: 0,0,0 (stringa)
    @Column(name = "rgb", length = 20)
    private String rgb;

    public ColorOption() {}

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

    public String getHex() {
        return hex;
    }

    public void setHex(String hex) {
        this.hex = hex;
    }

    public String getRgb() {
        return rgb;
    }

    public void setRgb(String rgb) {
        this.rgb = rgb;
    }
}