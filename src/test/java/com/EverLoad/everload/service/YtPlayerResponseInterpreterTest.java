package com.EverLoad.everload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YtPlayerResponseInterpreterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void interpretPrefersPlainWebmAudioOverMp4() throws Exception {
        JsonNode response = mapper.readTree("""
                {
                  "playabilityStatus": {"status": "OK"},
                  "videoDetails": {"lengthSeconds": "180"},
                  "streamingData": {"adaptiveFormats": [
                    {"mimeType": "audio/mp4", "url": "https://audio.test/m4a", "contentLength": "100"},
                    {"mimeType": "video/mp4", "url": "https://audio.test/video"},
                    {"mimeType": "audio/webm", "url": "https://audio.test/webm", "contentLength": "200"}
                  ]}
                }
                """);

        YtStreamResolution result = YtPlayerResponseInterpreter.interpret(
                response, YtMusicClient.WEB_REMIX, "test-resolver");

        assertTrue(result.isSuccess());
        assertEquals("webm", result.streamInfo().getFormat());
        assertEquals("https://audio.test/webm", result.streamInfo().getUrl());
        assertEquals(180L, result.streamInfo().getDurationSeconds());
    }

    @Test
    void interpretReportsMissingUnsignedAudioAndPlayabilityReason() throws Exception {
        JsonNode playableWithoutUrl = mapper.readTree("""
                {"playabilityStatus":{"status":"OK"},
                 "streamingData":{"adaptiveFormats":[{"mimeType":"audio/webm","signatureCipher":"locked"}]}}
                """);
        JsonNode blocked = mapper.readTree("""
                {"playabilityStatus":{"status":"UNPLAYABLE","reason":"Not available here"}}
                """);

        YtStreamResolution missing = YtPlayerResponseInterpreter.interpret(
                playableWithoutUrl, YtMusicClient.WEB_REMIX, "test");
        YtStreamResolution unavailable = YtPlayerResponseInterpreter.interpret(
                blocked, YtMusicClient.WEB_REMIX, "test");

        assertFalse(missing.isSuccess());
        assertTrue(missing.reason().contains("WEB_REMIX"));
        assertEquals("Not available here", unavailable.reason());
    }
}
