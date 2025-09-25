package com.EverLoad.everload;

import com.EverLoad.everload.model.Download;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadTest {

    @Test
    void constructorSinArgs_inicializaCreatedAt() {
        Download d = new Download();

        assertThat(d.getCreatedAt()).isNotNull();
        assertThat(d.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void constructorConArgs_asignaValoresYCreatedAt() {
        Download d = new Download("Canción X", "music", "YouTube");

        assertThat(d.getTitle()).isEqualTo("Canción X");
        assertThat(d.getType()).isEqualTo("music");
        assertThat(d.getPlatform()).isEqualTo("YouTube");
        assertThat(d.getCreatedAt()).isNotNull();
    }

    @Test
    void settersYgetters_funcionanCorrectamente() {
        Download d = new Download();
        d.setTitle("Video Y");
        d.setType("video");
        d.setPlatform("TikTok");

        LocalDateTime now = LocalDateTime.now();
        d.setCreatedAt(now);

        assertThat(d.getTitle()).isEqualTo("Video Y");
        assertThat(d.getType()).isEqualTo("video");
        assertThat(d.getPlatform()).isEqualTo("TikTok");
        assertThat(d.getCreatedAt()).isEqualTo(now);
    }


}