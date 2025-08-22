package com.EverLoad.everload;

import com.EverLoad.everload.model.Descarga;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DescargaTest {

    @Test
    void noArgsConstructor_and_SettersWork() {
        Descarga d = new Descarga();

        LocalDateTime fecha = LocalDateTime.of(2025, 1, 1, 12, 0);

        d.setTitulo("David Guetta");
        d.setTipo("música");
        d.setPlataforma("YouTube");
        d.setFecha(fecha);

        assertEquals("David Guetta", d.getTitulo());
        assertEquals("música", d.getTipo());
        assertEquals("YouTube", d.getPlataforma());
        assertEquals(fecha, d.getFecha());
    }

    @Test
    void allArgsConstructor_setsFields_and_FechaIsNow() {
        LocalDateTime before = LocalDateTime.now();
        Descarga d = new Descarga("David Guetta", "música", "Spotify");
        LocalDateTime after = LocalDateTime.now();

        assertEquals("David Guetta", d.getTitulo());
        assertEquals("música", d.getTipo());
        assertEquals("Spotify", d.getPlataforma());
        assertNotNull(d.getFecha());

        // La fecha debe estar entre before y after
        assertFalse(d.getFecha().isBefore(before), "fecha es anterior a 'before'");
        assertFalse(d.getFecha().isAfter(after), "fecha es posterior a 'after'");
    }

    @Test
    void setters_allowNulls() {
        Descarga d = new Descarga();
        d.setTitulo(null);
        d.setTipo(null);
        d.setPlataforma(null);
        d.setFecha(null);

        assertNull(d.getTitulo());
        assertNull(d.getTipo());
        assertNull(d.getPlataforma());
        assertNull(d.getFecha());
    }
}