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


}