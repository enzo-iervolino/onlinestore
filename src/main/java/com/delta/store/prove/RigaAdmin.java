package com.delta.store.prove;

public class RigaAdmin {

    private Long id;
    private String nome;
    private String email;
    private Boolean attivo;
    private String telefono;
    private String citta;
    private String dataCreazione;

    public RigaAdmin(Long id, String nome, String email, Boolean attivo, String telefono, String citta, String dataCreazione) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.attivo = attivo;
        this.telefono = telefono;
        this.citta = citta;
        this.dataCreazione = dataCreazione;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public Boolean getAttivo() {
        return attivo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCitta() {
        return citta;
    }

    public String getDataCreazione() {
        return dataCreazione;
    }
}