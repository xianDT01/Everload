package com.everload.everload.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YtDlpProgressParserTest {

    @Test
    void parsesIntegerAndDecimalProgress() {
        assertEquals(42, YtDlpProgressParser.parse("[download] 42%").orElseThrow());
        assertEquals(73, YtDlpProgressParser.parse("[download] 73.8% of 12MiB").orElseThrow());
    }

    @Test
    void ignoresLinesWithoutAValidPercentage() {
        assertTrue(YtDlpProgressParser.parse("download pending").isEmpty());
        assertTrue(YtDlpProgressParser.parse("download .%").isEmpty());
    }
}
