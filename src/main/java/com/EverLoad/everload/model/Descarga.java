package com.EverLoad.everload.model;

import java.time.LocalDateTime;

public class Descarga {
    private String titulo;
    private String tipo; // "música" o "vídeo"
    private String plataforma; // "YouTube", "Spotify", "TikTok", etc.
    private LocalDateTime fecha;

    public Descarga() {}

    public Descarga(String titulo, String tipo, String plataforma) {
        this.titulo = titulo;
        this.tipo = tipo;
        this.plataforma = plataforma;
        this.fecha = LocalDateTime.now();
    }

    // Getters y setters
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getPlataforma() { return plataforma; }
    public void setPlataforma(String plataforma) { this.plataforma = plataforma; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}