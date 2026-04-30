package com.delta.store.web.shop;

 
/**
 * Dati cliente/spedizione usati in checkout.
 *
 * NOTA: i nomi campi sono allineati agli input di checkout.html
 * (telefono, indirizzo, cap, citta, provincia) per evitare conflitti.
 */
public class CheckoutShippingForm {

    private String email;
    private String telefono;
    private String fullName;

    private String indirizzo;
    private String cap;
    private String citta;
    private String provincia;

    public CheckoutShippingForm() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getIndirizzo() {
        return indirizzo;
    }

    public void setIndirizzo(String indirizzo) {
        this.indirizzo = indirizzo;
    }

    public String getCap() {
        return cap;
    }

    public void setCap(String cap) {
        this.cap = cap;
    }

    public String getCitta() {
        return citta;
    }

    public void setCitta(String citta) {
        this.citta = citta;
    }

    public String getProvincia() {
        return provincia;
    }

    public void setProvincia(String provincia) {
        this.provincia = provincia;
    }

    // =====================
    // Utility
    // =====================
    public void normalize() {
        this.email = trimToNull(this.email);
        this.telefono = trimToNull(this.telefono);
        this.fullName = trimToNull(this.fullName);
        this.indirizzo = trimToNull(this.indirizzo);
        this.cap = trimToNull(this.cap);
        this.citta = trimToNull(this.citta);
        this.provincia = trimToNull(this.provincia);
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
