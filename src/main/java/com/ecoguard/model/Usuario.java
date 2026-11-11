package com.ecoguard.model;

public class Usuario {
    private final int id;
    private final String nome;
    private final String email;
    private final String perfil;

    public Usuario(int id, String nome, String email, String perfil) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.perfil = perfil;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getPerfil() { return perfil; }
}
