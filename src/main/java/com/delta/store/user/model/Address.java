package com.delta.store.user.model;

import jakarta.persistence.Embeddable;

@Embeddable
public class Address {

    private String street;
    private String zipcode;
    private String city;
    private String province;

    public Address() {
    }

    public Address(String street, String zipcode, String city, String province) {
        this.street = street;
        this.zipcode = zipcode;
        this.city = city;
        this.province = province;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getZipcode() {
        return zipcode;
    }

    public void setZipcode(String zipcode) {
        this.zipcode = zipcode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }
}

